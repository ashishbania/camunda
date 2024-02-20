/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import static io.camunda.operate.schema.SchemaManager.*;
import static io.camunda.operate.util.CollectionUtil.filter;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexSchemaValidator;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Migrates an operate schema from one version to another. Requires an already created destination
 * schema provided by a schema manager. Tries to detect source/previous schema if not provided.
 */
@Component
@Configuration
public class Migrator {

  private static final Logger logger = LoggerFactory.getLogger(Migrator.class);

  @Autowired private List<IndexDescriptor> indexDescriptors;

  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired private OperateProperties operateProperties;

  @Autowired private SchemaManager schemaManager;

  @Autowired private StepsRepository stepsRepository;

  @Autowired private MigrationProperties migrationProperties;

  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @Autowired private BeanFactory beanFactory;

  @Bean("migrationThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(migrationProperties.getThreadsCount());
    executor.setMaxPoolSize(migrationProperties.getThreadsCount());
    executor.setThreadNamePrefix("migration_");
    executor.initialize();
    return executor;
  }

  public void migrate() throws MigrationException {
    try {
      stepsRepository.updateSteps();
    } catch (IOException e) {
      throw new MigrationException(String.format("Migration failed due to %s", e.getMessage()));
    }
    boolean failed = false;
    List<Future<Boolean>> results =
        indexDescriptors.stream().map(this::migrateIndexInThread).collect(Collectors.toList());
    for (Future<Boolean> result : results) {
      try {
        if (!result.get()) {
          failed = true;
        }
      } catch (Exception e) {
        logger.error("Migration failed: ", e);
        failed = true;
      }
    }
    getTaskExecutor().shutdown();
    if (failed) {
      throw new MigrationException("Migration failed. See logging messages above.");
    }
  }

  private Future<Boolean> migrateIndexInThread(IndexDescriptor indexDescriptor) {
    return getTaskExecutor()
        .submit(
            () -> {
              try {
                migrateIndexIfNecessary(indexDescriptor);
              } catch (Exception e) {
                logger.error("Migration for {} failed:", indexDescriptor.getIndexName(), e);
                return false;
              }
              return true;
            });
  }

  private void migrateIndexIfNecessary(IndexDescriptor indexDescriptor)
      throws MigrationException, IOException {
    logger.info("Check if index {} needs to migrate.", indexDescriptor.getIndexName());
    Set<String> olderVersions = indexSchemaValidator.olderVersionsForIndex(indexDescriptor);
    if (olderVersions.size() > 1) {
      throw new MigrationException(
          String.format(
              "For index %s are existing more than one older versions: %s ",
              indexDescriptor.getIndexName(), olderVersions));
    }
    String currentVersion = indexDescriptor.getVersion();
    if (olderVersions.isEmpty()) {
      // find data initializer steps
      final List<Step> stepsForIndex =
          stepsRepository.findNotAppliedFor(indexDescriptor.getIndexName()).stream()
              .filter(s -> s instanceof DataInitializerStep)
              .collect(Collectors.toList());
      if (stepsForIndex.size() > 0) {
        Plan plan =
            createPlanFor(indexDescriptor.getIndexName(), "1.0.0", currentVersion, stepsForIndex);
        migrateIndex(indexDescriptor, plan);
      } else {
        logger.info(
            "No migration needed for {}, no previous indices found and no data initializer.",
            indexDescriptor.getIndexName());
      }
    } else {
      String olderVersion = olderVersions.iterator().next();
      final List<Step> stepsForIndex =
          stepsRepository.findNotAppliedFor(indexDescriptor.getIndexName());
      Plan plan =
          createPlanFor(
              indexDescriptor.getIndexName(), olderVersion, currentVersion, stepsForIndex);
      migrateIndex(indexDescriptor, plan);
      var indexPrefix =
          DatabaseInfo.isOpensearch()
              ? operateProperties.getOpensearch().getIndexPrefix()
              : operateProperties.getElasticsearch().getIndexPrefix();
      if (migrationProperties.isDeleteSrcSchema()) {
        String olderBaseIndexName =
            String.format("%s-%s-%s_", indexPrefix, indexDescriptor.getIndexName(), olderVersion);
        final String deleteIndexPattern = String.format("%s*", olderBaseIndexName);
        logger.info("Deleted previous indices for pattern {}", deleteIndexPattern);
        schemaManager.deleteIndicesFor(deleteIndexPattern);
        if (indexDescriptor instanceof TemplateDescriptor) {
          final String deleteTemplatePattern = String.format("%stemplate", olderBaseIndexName);
          logger.info("Deleted previous templates for {}", deleteTemplatePattern);
          schemaManager.deleteTemplatesFor(deleteTemplatePattern);
        }
      }
    }
  }

  public void migrateIndex(final IndexDescriptor indexDescriptor, final Plan plan)
      throws IOException, MigrationException {
    String refreshInterval;
    Integer numberOfReplicas;
    if (DatabaseInfo.isOpensearch()) {
      refreshInterval = operateProperties.getOpensearch().getRefreshInterval();
      numberOfReplicas = operateProperties.getOpensearch().getNumberOfReplicas();
    } else {
      refreshInterval = operateProperties.getElasticsearch().getRefreshInterval();
      numberOfReplicas = operateProperties.getElasticsearch().getNumberOfReplicas();
    }

    logger.debug("Save current settings for {}", indexDescriptor.getFullQualifiedName());
    final Map<String, String> indexSettings =
        getIndexSettingsOrDefaultsFor(indexDescriptor, refreshInterval, numberOfReplicas);

    logger.debug("Set reindex settings for {}", indexDescriptor.getDerivedIndexNamePattern());
    schemaManager.setIndexSettingsFor(
        Map.of(
            NUMBERS_OF_REPLICA, NO_REPLICA,
            REFRESH_INTERVAL, NO_REFRESH),
        indexDescriptor.getDerivedIndexNamePattern());

    logger.info("Execute plan: {} ", plan);
    plan.executeOn(schemaManager);

    logger.debug("Save applied steps in migration repository");
    for (final Step step : plan.getSteps()) {
      step.setApplied(true).setAppliedDate(OffsetDateTime.now());
      stepsRepository.save(step);
    }

    logger.debug("Restore settings for {}", indexDescriptor.getDerivedIndexNamePattern());
    schemaManager.setIndexSettingsFor(
        Map.of(
            NUMBERS_OF_REPLICA, indexSettings.get(NUMBERS_OF_REPLICA),
            REFRESH_INTERVAL, indexSettings.get(REFRESH_INTERVAL)),
        indexDescriptor.getDerivedIndexNamePattern());

    logger.info("Refresh index {}", indexDescriptor.getDerivedIndexNamePattern());
    schemaManager.refresh(indexDescriptor.getDerivedIndexNamePattern());

    plan.validateMigrationResults(schemaManager);
  }

  private Map<String, String> getIndexSettingsOrDefaultsFor(
      final IndexDescriptor indexDescriptor, String refreshInterval, Integer numberOfReplicas) {
    Map<String, String> settings = new HashMap<>();
    settings.put(
        REFRESH_INTERVAL,
        schemaManager.getOrDefaultRefreshInterval(
            indexDescriptor.getFullQualifiedName(), refreshInterval));
    settings.put(
        NUMBERS_OF_REPLICA,
        schemaManager.getOrDefaultNumbersOfReplica(
            indexDescriptor.getFullQualifiedName(), "" + numberOfReplicas));
    return settings;
  }

  protected Plan createPlanFor(
      final String indexName,
      final String srcVersion,
      final String dstVersion,
      final List<Step> steps)
      throws MigrationException {
    final SemanticVersion sourceVersion = SemanticVersion.fromVersion(srcVersion);
    final SemanticVersion destinationVersion = SemanticVersion.fromVersion(dstVersion);

    final List<Step> sortByVersion = new ArrayList<>(steps);
    sortByVersion.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);

    final List<Step> onlyAffectedVersions =
        filter(
            sortByVersion,
            s ->
                SemanticVersion.fromVersion(s.getVersion())
                    .isBetween(sourceVersion, destinationVersion));

    String indexPrefix =
        DatabaseInfo.isOpensearch()
            ? operateProperties.getOpensearch().getIndexPrefix()
            : operateProperties.getElasticsearch().getIndexPrefix();
    final String srcIndex = String.format("%s-%s-%s", indexPrefix, indexName, srcVersion);
    final String dstIndex = String.format("%s-%s-%s", indexPrefix, indexName, dstVersion);

    // forbid migration when migration steps can't be combined
    if (onlyAffectedVersions.stream().anyMatch(s -> s instanceof ProcessorStep)
        && onlyAffectedVersions.stream().anyMatch(s -> s instanceof SetBpmnProcessIdStep)) {
      throw new MigrationException(
          "Migration plan contains steps that can't be applied together. Check your upgrade path.");
    }
    if (onlyAffectedVersions.size() == 0) {
      final ReindexPlan reindexPlan = beanFactory.getBean(ReindexPlan.class);
      return reindexPlan.setSrcIndex(srcIndex).setDstIndex(dstIndex);
    } else if (onlyAffectedVersions.get(0) instanceof ProcessorStep) {
      final ReindexPlan reindexPlan = beanFactory.getBean(ReindexPlan.class);
      return reindexPlan.setSrcIndex(srcIndex).setDstIndex(dstIndex).setSteps(onlyAffectedVersions);
    } else if (onlyAffectedVersions.get(0) instanceof SetBpmnProcessIdStep
        && onlyAffectedVersions.size() == 1) {
      // we don't include version in list-view index name, as we can't know which version we have -
      // older or newer
      final String listViewIndexName =
          String.format("%s-%s", indexPrefix, listViewTemplate.getIndexName());
      ReindexWithQueryAndScriptPlan reindexPlan =
          beanFactory.getBean(ReindexWithQueryAndScriptPlan.class);
      return reindexPlan
          .setSrcIndex(srcIndex)
          .setDstIndex(dstIndex)
          .setListViewIndexName(listViewIndexName)
          .setSteps(onlyAffectedVersions);
    } else if (onlyAffectedVersions.get(0) instanceof FillPostImporterQueueStep
        && onlyAffectedVersions.size() == 1) {
      final FillPostImporterQueuePlan fillPostImporterQueuePlan =
          beanFactory.getBean(FillPostImporterQueuePlan.class);
      return fillPostImporterQueuePlan
          .setListViewIndexName(
              String.format("%s-%s", indexPrefix, listViewTemplate.getIndexName()))
          .setIncidentsIndexName(
              String.format("%s-%s", indexPrefix, incidentTemplate.getIndexName()))
          .setPostImporterQueueIndexName(postImporterQueueTemplate.getFullQualifiedName())
          .setSteps(onlyAffectedVersions);
    } else if ((onlyAffectedVersions.get(0) instanceof SetBpmnProcessIdStep
            || onlyAffectedVersions.get(0) instanceof FillPostImporterQueueStep)
        && onlyAffectedVersions.size() > 1) {
      throw new MigrationException(
          "Unexpected migration plan: only one step of this type must be present: "
              + onlyAffectedVersions.get(0).getClass().getSimpleName());
    } else {
      throw new MigrationException("Unexpected migration plan.");
    }
  }
}
