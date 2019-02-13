import React from 'react';
import PropTypes from 'prop-types';
import Pill from 'modules/components/Pill';

import {PILL_TYPE} from 'modules/constants';

import {withFlowNodeTimeStampContext} from 'modules/contexts/FlowNodeTimeStampContext';

class TimeStampPill extends React.Component {
  static propTypes = {
    onTimeStampToggle: PropTypes.func.isRequired,
    showTimeStamp: PropTypes.bool.isRequired
  };

  render() {
    const {showTimeStamp, onTimeStampToggle} = this.props;
    return (
      <Pill
        isActive={showTimeStamp}
        onClick={onTimeStampToggle}
        type={PILL_TYPE.TIMESTAMP}
      >
        {`${showTimeStamp ? 'Hide' : 'Show'} End Time`}
      </Pill>
    );
  }
}
export default withFlowNodeTimeStampContext(TimeStampPill);
