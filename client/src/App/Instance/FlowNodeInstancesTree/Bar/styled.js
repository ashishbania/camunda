import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import BasicFlowNodeIcon from 'modules/components/FlowNodeIcon';

export const NodeIcon = styled(BasicFlowNodeIcon)``;

export const Bar = themed(styled.div`
  display: flex;
  height: 27px;
  font-size: 13px;
  min-width: 200px;
  align-items: center;
  flex-grow: 1;
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};
  border-width: 0px 0px 1px 1px;
  border-style: solid;
  color: ${({isSelected}) => isSelected && '#fff'};

  > svg {
    fill: ${({isSelected}) => isSelected && '#fff'};
  }

  /* Border between Icon and Name */
  > span {
    border-color: ${({isSelected}) => isSelected && 'rgba(255, 255, 255, 0.9)'};
  }
`);

export const NodeName = themed(styled.span`
  border-left: 1px solid
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  margin-left: 5px;
  padding-left: 5px;
  font-weight: ${({isBold}) => (isBold ? 'bold' : '')};
`);
