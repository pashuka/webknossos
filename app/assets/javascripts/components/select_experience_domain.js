// @flow

import * as React from "react";
import { Select } from "antd";
import type { ExperienceDomainListType } from "admin/api_flow_types";
import { getExistingExperienceDomains } from "admin/admin_rest_api";

const Option = Select.Option;

type Props = {
  value: ?string | ?Array<string>,
  width: number,
  placeholder: string,
  notFoundContent: ?string,
  disabled: boolean,
  mode: string,
  onSelect: string => void,
  onChange: () => void,
  alreadyUsedDomains: ExperienceDomainListType,
};

type State = {
  domains: ExperienceDomainListType,
};

class SelectExperienceDomain extends React.PureComponent<Props, State> {
  static defaultProps = {
    mode: "default",
    alreadyUsedDomains: [],
    onChange: () => {},
    onSelect: () => {},
    value: null,
    notFoundContent: null,
    disabled: false,
  };

  state = {
    domains: [],
  };

  componentDidMount() {
    this.fetchData();
  }

  async fetchData() {
    this.setState({ domains: await getExistingExperienceDomains() });
  }

  getUnusedDomains(): ExperienceDomainListType {
    return this.state.domains.filter(domain => !this.props.alreadyUsedDomains.includes(domain));
  }

  render() {
    return (
      <Select
        showSearch
        mode={this.props.mode}
        value={this.props.value}
        optionFilterProp="children"
        notFoundContent={this.props.notFoundContent}
        style={{ width: `${this.props.width}%` }}
        disabled={this.props.disabled}
        placeholder={this.props.placeholder}
        onSelect={this.props.onSelect}
        onChange={this.props.onChange}
      >
        {this.getUnusedDomains().map(domain => <Option key={domain}>{domain}</Option>)}
      </Select>
    );
  }
}

export default SelectExperienceDomain;
