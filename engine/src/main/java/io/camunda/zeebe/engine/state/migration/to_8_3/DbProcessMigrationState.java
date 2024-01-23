/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.deployment.Digest;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.engine.state.deployment.VersionInfo;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.collections.MutableInteger;

public final class DbProcessMigrationState {
  private final DbLong processDefinitionKey;
  private final PersistedProcess persistedProcess;

  /** [process definition key] => process */
  private final ColumnFamily<DbLong, PersistedProcess> deprecatedProcessCacheColumnFamily;

  private final DbString tenantIdKey;
  private final DbTenantAwareKey<DbLong> tenantAwareProcessDefinitionKey;

  /** [tenant id | process definition key] => process */
  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedProcess> processColumnFamily;

  private final DbString processId;
  private final DbLong processVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;

  /** [process id | process version] => process */
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, PersistedProcess>
      deprecatedProcessCacheByIdAndVersionColumnFamily;

  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>
      tenantAwareProcessIdAndVersionKey;

  /** [tenant id | process id | process version] => process */
  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedProcess>
      processByIdAndVersionColumnFamily;

  private final Digest digest;
  private final DbForeignKey<DbString> fkProcessId;

  /** [process id] => digest */
  private final ColumnFamily<DbForeignKey<DbString>, Digest> deprecatedDigestByIdColumnFamily;

  private final DbTenantAwareKey<DbString> tenantAwareProcessId;
  private final DbForeignKey<DbTenantAwareKey<DbString>> fkTenantAwareProcessId;

  /** [tenant id | process id] => digest */
  private final ColumnFamily<DbForeignKey<DbTenantAwareKey<DbString>>, Digest>
      digestByIdColumnFamily;

  private final DbString processIdKey;
  private final VersionInfo versionInfo;

  /** [process id] => version info */
  private final ColumnFamily<DbString, VersionInfo> deprecatedProcessVersionColumnFamily;

  private final DbString idKey;
  private final DbTenantAwareKey<DbString> tenantAwareIdKey;

  /** [tenant id | (process) id] => version info */
  private final ColumnFamily<DbTenantAwareKey<DbString>, VersionInfo> versionInfoColumnFamily;

  private final ZeebeDb<ZbColumnFamilies> zeebeDb;

  public DbProcessMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    this.zeebeDb = zeebeDb;
    final var ctx = zeebeDb.createContext();
    processDefinitionKey = new DbLong();
    persistedProcess = new PersistedProcess();
    deprecatedProcessCacheColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_PROCESS_CACHE, ctx, processDefinitionKey, persistedProcess);

    tenantIdKey = new DbString();
    tenantAwareProcessDefinitionKey =
        new DbTenantAwareKey<>(tenantIdKey, processDefinitionKey, PlacementType.PREFIX);
    processColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE, ctx, tenantAwareProcessDefinitionKey, persistedProcess);

    processId = new DbString();
    processVersion = new DbLong();
    idAndVersionKey = new DbCompositeKey<>(processId, processVersion);
    deprecatedProcessCacheByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_PROCESS_CACHE_BY_ID_AND_VERSION,
            ctx,
            idAndVersionKey,
            persistedProcess);

    tenantAwareProcessIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    processByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            ctx,
            tenantAwareProcessIdAndVersionKey,
            persistedProcess);

    digest = new Digest();
    fkProcessId =
        new DbForeignKey<>(
            processId,
            ZbColumnFamilies.DEPRECATED_PROCESS_CACHE_BY_ID_AND_VERSION,
            MatchType.Prefix);
    deprecatedDigestByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_PROCESS_CACHE_DIGEST_BY_ID, ctx, fkProcessId, digest);

    tenantAwareProcessId = new DbTenantAwareKey<>(tenantIdKey, processId, PlacementType.PREFIX);
    fkTenantAwareProcessId =
        new DbForeignKey<>(
            tenantAwareProcessId,
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            MatchType.Prefix);
    digestByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_DIGEST_BY_ID, ctx, fkTenantAwareProcessId, digest);

    processIdKey = new DbString();
    versionInfo = new VersionInfo();
    deprecatedProcessVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_PROCESS_VERSION, ctx, processIdKey, versionInfo);

    idKey = new DbString();
    tenantAwareIdKey = new DbTenantAwareKey<>(tenantIdKey, idKey, PlacementType.PREFIX);
    versionInfoColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_VERSION, ctx, tenantAwareIdKey, versionInfo);
  }

  public void migrateProcessStateForMultiTenancy() {
    final var memoryUsage = new MutableInteger();
    final var txnLimit = 100 * 1024 * 1024;
    tenantIdKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    while (!deprecatedProcessCacheColumnFamily.isEmpty()) {
      memoryUsage.set(0);
      deprecatedProcessCacheColumnFamily.whileTrue(
          (key, value) -> {
            value.setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
            processDefinitionKey.wrapLong(key.getValue());
            processColumnFamily.insert(tenantAwareProcessDefinitionKey, value);
            deprecatedProcessCacheColumnFamily.deleteExisting(key);

            return memoryUsage.addAndGet(key.getLength() + value.getLength()) < txnLimit;
          });
    }
    Loggers.STREAM_PROCESSING.debug("Finished migrating deprecatedProcessCacheColumnFamily");

    while (!deprecatedProcessCacheByIdAndVersionColumnFamily.isEmpty()) {
      memoryUsage.set(0);
      deprecatedProcessCacheByIdAndVersionColumnFamily.whileTrue(
          (key, value) -> {
            value.setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
            processId.wrapBuffer(value.getBpmnProcessId());
            processVersion.wrapLong(value.getVersion());
            processByIdAndVersionColumnFamily.insert(tenantAwareProcessIdAndVersionKey, value);
            deprecatedProcessCacheByIdAndVersionColumnFamily.deleteExisting(key);

            return memoryUsage.addAndGet(key.getLength() + value.getLength()) < txnLimit;
          });
    }
    Loggers.STREAM_PROCESSING.debug(
        "Finished migrating deprecatedProcessCacheByIdAndVersionColumnFamily");

    while (!deprecatedDigestByIdColumnFamily.isEmpty()) {
      memoryUsage.set(0);
      deprecatedDigestByIdColumnFamily.whileTrue(
          (key, value) -> {
            processId.wrapBuffer(key.inner().getBuffer());
            digestByIdColumnFamily.insert(fkTenantAwareProcessId, value);
            deprecatedDigestByIdColumnFamily.deleteExisting(key);

            return memoryUsage.addAndGet(key.getLength() + value.getLength()) < txnLimit;
          });
    }
    Loggers.STREAM_PROCESSING.debug("Finished migrating deprecatedDigestByIdColumnFamily");

    while (!deprecatedProcessVersionColumnFamily.isEmpty()) {
      memoryUsage.set(0);
      deprecatedProcessVersionColumnFamily.whileTrue(
          (key, value) -> {
            idKey.wrapBuffer(key.getBuffer());

            final long highestVersion = value.getHighestVersion();
            for (long version = 1; version <= highestVersion; version++) {
              if (!value.getKnownVersions().contains(version)) {
                value.addKnownVersion(version);
              }
            }

            versionInfoColumnFamily.insert(tenantAwareIdKey, value);
            deprecatedProcessVersionColumnFamily.deleteExisting(key);

            return memoryUsage.addAndGet(key.getLength() + value.getLength()) < txnLimit;
          });
    }
    Loggers.STREAM_PROCESSING.debug("Finished migrating deprecatedProcessVersionColumnFamily");
  }
}
