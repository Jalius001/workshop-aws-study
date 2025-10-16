package com.ssp.infrastructure;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateServiceProps;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueProps;
import software.constructs.Construct;

import java.util.List;

import static com.ssp.infrastructure.EnvironmentType.PROD;
import static java.lang.String.format;

public class ApplicationStack extends Stack {
    @Getter
    public ApplicationLoadBalancedFargateService fargateService ;
    @Getter
    public ApplicationTargetGroup greenTargetGroup ;
    @Getter
    public ApplicationListener greenLoadBalancerListener ;


    public ApplicationStack(@Nullable Construct scope, @Nullable String id, @Nullable ApplicationStackProps props) {
        super(scope, format("%s-app-stack", id), props);

        Vpc vpc = new Vpc(this, format("%s-Vpc", id));
        Cluster ecsCluster = new Cluster(this, format("%s-EcsCluster", id), new ClusterProps() {
            @Override
            public IVpc getVpc() {
                return vpc;
            }
        });

        if (PROD.getId().equals(id)) {
            fargateService = new ApplicationLoadBalancedFargateService(this, format("%s-FargateService", id), ApplicationLoadBalancedFargateServiceProps.builder()
                    .cluster(ecsCluster)
                    .publicLoadBalancer(true)
                    .memoryLimitMiB(1024)
                    .cpu(512)
                    .desiredCount(1)
                    .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                            .image(ContainerImage.fromEcrRepository(props.getEcrRepository()))
                            .containerName("my-app")
                            .containerPort(8081)
                            .build())
                    .deploymentController(DeploymentController.builder()
                            .type(DeploymentControllerType.CODE_DEPLOY)
                            .build())
                    .build());
            greenLoadBalancerListener = fargateService.getLoadBalancer().addListener(format("%s-GreenLoadBalancerListener", id), BaseApplicationListenerProps.builder()
                    .port(81)
                    .protocol(ApplicationProtocol.HTTP)
                    .build());
            greenTargetGroup = new ApplicationTargetGroup(this, format("%s-GreenTargetGroup", id), ApplicationTargetGroupProps.builder()
                    .port(80)
                    .targetType(TargetType.IP)
                    .vpc(vpc)
                    .build());
            greenTargetGroup.configureHealthCheck(HealthCheck.builder()
                    .timeout(Duration.seconds(10))
                    .unhealthyThresholdCount(2)
                    .healthyThresholdCount(2)
                    .interval(Duration.seconds(11))
                    .path("/my-app")
                    .build());
            greenLoadBalancerListener.addTargetGroups(format("%s-GreenListener", id), AddApplicationTargetGroupsProps.builder()
                    .targetGroups(List.of(greenTargetGroup))
                    .build());
        } else {
            //Test service definition
            fargateService = new ApplicationLoadBalancedFargateService(this, format("%s-FargateService", id), ApplicationLoadBalancedFargateServiceProps.builder()
                    .cluster(ecsCluster)
                    .publicLoadBalancer(true)
                    .memoryLimitMiB(1024)
                    .cpu(512)
                    .desiredCount(1)
                    .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                            .image(ContainerImage.fromEcrRepository(props.getEcrRepository()))
                            .containerName("my-app")
                            .containerPort(8081)
                            .build())
                    .build());
            fargateService.getTargetGroup().configureHealthCheck(HealthCheck.builder()
                    .healthyThresholdCount(2)
                    .unhealthyThresholdCount(2)
                    .timeout(Duration.seconds(10))
                    .interval(Duration.seconds(11))
                    .path("/my-app")
                    .build());
            fargateService.getTargetGroup().setAttribute(
                    "deregistration_delay.timeout_seconds", "5");
        }
    }
}
