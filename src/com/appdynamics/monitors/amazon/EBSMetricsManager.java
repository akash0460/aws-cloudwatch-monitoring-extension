package com.appdynamics.monitors.amazon;

import com.amazonaws.services.cloudwatch.model.*;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class EBSMetricsManager extends MetricsManager{

    private static final String NAMESPACE = "AWS/EBS";
    private Logger logger = Logger.getLogger(this.getClass().getName());

    public EBSMetricsManager(AmazonCloudWatchMonitor amazonCloudWatchMonitor){
        super(amazonCloudWatchMonitor);
    }

    @Override
    public Object gatherMetrics() {
        HashMap<String, HashMap<String, List<Datapoint>>> ebsMetrics = new HashMap<String, HashMap<String,List<Datapoint>>>();
        List<DimensionFilter> filter = new ArrayList<DimensionFilter>();
        DimensionFilter volumeIdFilter = new DimensionFilter();
        volumeIdFilter.setName("VolumeId");
        filter.add(volumeIdFilter);
        ListMetricsRequest listMetricsRequest = new ListMetricsRequest();
        listMetricsRequest.setDimensions(filter);
        ListMetricsResult ebsMetricsResult = awsCloudWatch.listMetrics(listMetricsRequest);
        List<com.amazonaws.services.cloudwatch.model.Metric> ebsMetricsResultMetrics = ebsMetricsResult.getMetrics();

        for (com.amazonaws.services.cloudwatch.model.Metric m : ebsMetricsResultMetrics) {
            List<Dimension> dimensions = m.getDimensions();
            for (Dimension dimension : dimensions) {
                if (!ebsMetrics.containsKey(dimension.getValue())) {
                    ebsMetrics.put(dimension.getValue(), new HashMap<String,List<Datapoint>>());
                }
                gatherEBSMetricsHelper(m, dimension, ebsMetrics);
            }
        }
        return ebsMetrics;
    }
    private void gatherEBSMetricsHelper(com.amazonaws.services.cloudwatch.model.Metric metric,
                                             Dimension dimension,
                                             HashMap<String, HashMap<String, List<Datapoint>>> ebsMetrics) {
        if(disabledMetrics.get(NAMESPACE).contains(metric.getMetricName())) {
            return;
        }
        GetMetricStatisticsRequest getMetricStatisticsRequest = amazonCloudWatchMonitor.createGetMetricStatisticsRequest(NAMESPACE, metric.getMetricName(), "Average", null);
        GetMetricStatisticsResult getMetricStatisticsResult = awsCloudWatch.getMetricStatistics(getMetricStatisticsRequest);
        ebsMetrics.get(dimension.getValue()).put(metric.getMetricName(), getMetricStatisticsResult.getDatapoints());
    }

    @Override
    public void printMetrics(Object metrics) {
        HashMap<String, HashMap<String,List<Datapoint>>> ebsMetrics = (HashMap<String,HashMap<String,List<Datapoint>>>) metrics;
        Iterator outerIterator = ebsMetrics.keySet().iterator();

        while (outerIterator.hasNext()) {
            String volumeId = outerIterator.next().toString();
            HashMap<String, List<Datapoint>> metricStatistics = ebsMetrics.get(volumeId);
            Iterator innerIterator = metricStatistics.keySet().iterator();
            while (innerIterator.hasNext()) {
                String metricName = innerIterator.next().toString();
                List<Datapoint> datapoints = metricStatistics.get(metricName);
                if (datapoints != null && !datapoints.isEmpty()) {
                    Datapoint data = datapoints.get(0);
                    amazonCloudWatchMonitor.printMetric(getNamespacePrefix(), volumeId + "|" + metricName + "(" + data.getUnit() + ")", data.getAverage(),
                            MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
                            MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,
                            MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL);

                }
            }
        }
        logger.info("--------PRINTING " + NAMESPACE + " METRICS---------");
    }

    @Override
    public String getNamespacePrefix() {
        return NAMESPACE + "|" + "VolumeId|";
    }
}
