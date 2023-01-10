local grafana = import 'grafonnet/grafana.libsonnet';
local influxdb = grafana.influxdb;
local graphPanel = grafana.graphPanel;
local alertCondition = grafana.alertCondition;
local dashboard = grafana.dashboard;

local serverGroup_instances = [
  'Atl-Atlanta',
  'Ewr-NewJersey',
  'HS2-AS-1',
  'HS2-AS-1-AU',
  'HS2-AS-11',
  'HS2-AS-2',
  'HS2-AS-3',
  'HS2-AS-4',
  'HS2-AS-5',
  'HS2-AS-6',
  'HS2-AS-7',
  'HS2-AS-8',
  'HS2-AS-9',
  'HS2-NS-AU',
  'HS2-NS-Sip',
  'HS2-NS-Usr',
  'HS3-AS-1',
  'HS3-AS-1-EU',
  'HS3-AS-11',
  'HS3-AS-2',
  'HS3-AS-3',
  'HS3-AS-4',
  'HS3-AS-5',
  'HS3-AS-6',
  'HS3-AS-7',
  'HS3-AS-8',
  'HS3-AS-9',
  'HS3-NS-EU',
  'HS3-NS-Sip',
  'HS3-NS-Usr',
  'Kamailio',
  'Telnyx-Chicago',
  'Telnyx-Washington',
];

local environment = 'wdfwwxc-int-2';

local serverGroupAlertTimeSeries(environment, serverGroup_instance) =
  graphPanel.new(
    title='ServerGroup - ' + environment + ' : ' + serverGroup_instance,
    datasource='influx_microservice_metrics',
  ).addTarget(
    influxdb.target(
      query='SELECT status FROM \"dhruva.sgMetric\" WHERE \"environment\" = \'' + environment + '\' AND \"sgName\" = \'' + serverGroup_instance + "'"
    )
    .selectField('value')
    .addConverter('mean')
  ).addAlert(
    'ServerGroup: ' + environment + ' : ' + serverGroup_instance + ' down alert',
    message='Hello Team,\n\nServer group down \n\nsgName: ' + serverGroup_instance + '\n\nEnvironment: ' + environment,
    forDuration='2m',
    frequency='1m',
    notifications=[{ uid: '2Tlzb-FVk' }]
  ).addCondition(
    alertCondition.new(
      evaluatorType='lt',
      evaluatorParams=[0.5],
      operatorType='and',
      reducerType='last',
      queryRefId='A',
      queryTimeStart='1m',
      queryTimeEnd='now',
    ),
  );

local generateDBPanel(serverGroup_instance) =
  serverGroupAlertTimeSeries(environment, serverGroup_instance) {
    gridPos: { h: 6, w: 24, x: 0, y: 0 },
  };

dashboard.new(
  'ServerGroup Down Alerting ENV - ' + environment,
  uid='90C6DB2A',
  editable=true,
  style='dark',
  tags=['dhruva', 'alerting'],
  graphTooltip='shared_crosshair',
  description='Alerting for servergroup',
  time_from='now-1h',
  time_to='now',
).addPanels(std.map(generateDBPanel, serverGroup_instances))
