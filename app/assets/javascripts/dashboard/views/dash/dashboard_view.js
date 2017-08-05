// @flow
/* eslint-disable jsx-a11y/href-no-hash */

import React from "react";
import Request from "libs/request";
import { Tabs } from "antd";
import type { APIUserType } from "admin/api_flow_types";
import DatasetView from "./dataset_view";
import DashboardTaskListView from "./dashboard_task_list_view";

const TabPane = Tabs.TabPane;

const validTabKeys = ["datasets", "tasks", "explorativeAnnotations"];

type Props = {
  userID: ?string,
  isAdminView: boolean,
};

class DashboardView extends React.PureComponent {
  props: Props;

  state: {
    activeTabKey: string,
    user: ?APIUserType,
  };

  constructor(props: Props) {
    super(props);

    const lastUsedTabKey = localStorage.getItem("lastUsedDashboardTab");
    const isValid = lastUsedTabKey && validTabKeys.indexOf(lastUsedTabKey) > -1;
    this.state = {
      activeTabKey: lastUsedTabKey && isValid ? lastUsedTabKey : "datasets",
      user: null,
    };
  }

  componentDidMount() {
    this.fetchData();
  }

  async fetchData(): Promise<void> {
    const url = this.props.userID ? `/api/users/${this.props.userID}` : "/api/user";
    const user = await Request.receiveJSON(url);

    this.setState({
      user,
    });
  }

  getTabs() {
    const isAdmin = this.props.isAdminView;
    return [
      !isAdmin
        ? <TabPane tab="Datasets" key="datasets">
            <DatasetView user={this.state.user} />
          </TabPane>
        : null,
      <TabPane tab="Tasks" key="tasks">
        <DashboardTaskListView isAdminView={this.props.isAdminView} userID={this.props.userID} />
      </TabPane>,
      <TabPane tab="Explorative Annotations" key="explorativeAnnotations">
        Explorative Annotations
      </TabPane>,
      isAdmin
        ? <TabPane tab="Tracked Time" key="trackedTime">
            Content of Tab Pane 3
          </TabPane>
        : null,
    ];
  }

  render() {
    const user = this.state.user;
    if (!user) {
      return null;
    }

    const onTabChange = activeTabKey => {
      const isValid = validTabKeys.indexOf(activeTabKey) > -1;
      if (isValid) {
        localStorage.setItem("lastUsedDashboardTab", activeTabKey);
      }
      this.setState({ activeTabKey });
    };
    const userHeader = this.props.isAdminView
      ? <h3>
          User: ${user.firstName} ${user.lastName}
        </h3>
      : null;

    return (
      <div id="dashboard" className="container wide">
        {userHeader}
        <Tabs
          activeKey={this.state.activeTabKey}
          onChange={onTabChange}
          type="card"
          style={{ marginTop: 20 }}
        >
          {this.getTabs()}
        </Tabs>
      </div>
    );
  }
}

export default DashboardView;
