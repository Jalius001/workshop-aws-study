package com.ssp.infrastructure;

import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codeconnections.CfnConnection;
import software.amazon.awscdk.services.codeconnections.CfnConnectionProps;
import software.amazon.awscdk.services.codedeploy.*;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.events.*;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.RuleProps;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.events.targets.SnsTopicProps;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.TopicProps;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.ssm.StringParameterProps;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;


public class PipelineStack extends Stack {
    public PipelineStack(@Nullable Construct scope, @Nullable String id, @Nullable PipelineStackProps props) {
        super(scope, id, props);
        CfnConnection gitHubConnection = new CfnConnection(this, "GitHub_Jalius001", CfnConnectionProps.builder()
                .connectionName("GitHub Jalius001 Connection")
                .providerType("GitHub")
                .build());
        Pipeline pipeline = new Pipeline(this, "Pipeline", PipelineProps.builder()
                .pipelineName("CICD_Pipeline")
                .crossAccountKeys(false)
                .pipelineType(PipelineType.V2)
                .executionMode(ExecutionMode.QUEUED)
                .build());
        PipelineProject codeBuild = new PipelineProject(this, "CodeBuild", PipelineProjectProps.builder()
                .environment(BuildEnvironment.builder()
                        .buildImage(LinuxBuildImage.STANDARD_7_0)
                        .privileged(true)
                        .computeType(ComputeType.LARGE)
                        .build())
                .buildSpec(BuildSpec.fromSourceFilename("buildspec_codebuild.yml"))
                .build());
        Map<String, BuildEnvironmentVariable> environmentVariables = new HashMap<>();
        environmentVariables.put("IMAGE_TAG", BuildEnvironmentVariable.builder().type(BuildEnvironmentVariableType.PLAINTEXT).value("latest").build());
        environmentVariables.put("IMAGE_REPO_URI", BuildEnvironmentVariable.builder().type(BuildEnvironmentVariableType.PLAINTEXT).value(Optional.ofNullable(props).map(PipelineStackProps::getEcrRepository).map(Repository::getRepositoryUri).orElse("")).build());
        environmentVariables.put("AWS_DEFAULT_REGION", BuildEnvironmentVariable.builder().type(BuildEnvironmentVariableType.PLAINTEXT).value(System.getenv().get("CDK_DEFAULT_REGION")).build());
        PipelineProject dockerBuild = new PipelineProject(this, "DockerBuild", PipelineProjectProps.builder()
                .environmentVariables(environmentVariables)
                .environment(BuildEnvironment.builder()
                        .buildImage(LinuxBuildImage.STANDARD_7_0)
                        .privileged(true)
                        .computeType(ComputeType.LARGE)
                        .build())
                .buildSpec(BuildSpec.fromSourceFilename("buildspec_docker.yml"))
                .build());

        PolicyStatement dockerBuildRolePolicy = new PolicyStatement(PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(List.of("*"))
                .actions(List.of(
                        "ecr:GetAuthorizationToken",
                        "ecr:BatchCheckLayerAvailability",
                        "ecr:GetDownloadUrlForLayer",
                        "ecr:GetRepositoryPolicy",
                        "ecr:DescribeRepositories",
                        "ecr:ListImages",
                        "ecr:DescribeImages",
                        "ecr:BatchGetImage",
                        "ecr:InitiateLayerUpload",
                        "ecr:UploadLayerPart",
                        "ecr:CompleteLayerUpload",
                        "ecr:PutImage"))
                .build());
        dockerBuild.addToRolePolicy(dockerBuildRolePolicy);

        StringParameter signerARNParameter = new StringParameter(this, "SignerARNParam", StringParameterProps.builder()
                .parameterName("signer-profile-arn")
                .stringValue("arn:aws:signer:us-east-1:354196887646:/signing-profiles/ecr_signing_profile")
                .build());
        PolicyStatement signerParameterPolicy = new PolicyStatement(PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(List.of(signerARNParameter.getParameterArn()))
                .actions(List.of(
                        "ssm:GetParametersByPath",
                        "ssm:GetParameters"))
                .build());
        dockerBuild.addToRolePolicy(signerParameterPolicy);

        PolicyStatement signerPolicy = new PolicyStatement(PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(List.of("*"))
                .actions(List.of(
                        "signer:PutSigningProfile",
                        "signer:SignPayload",
                        "signer:GetRevocationStatus"))
                .build());
        dockerBuild.addToRolePolicy(signerPolicy);

        EcsApplication ecsCodeDeployApp = new EcsApplication(this, "my-app", EcsApplicationProps.builder()
                .applicationName("my-app")
                .build());
        EcsDeploymentGroup prodEcsDeploymentGroup = new EcsDeploymentGroup(this, "my-app-dg", EcsDeploymentGroupProps.builder()
                .service(Optional.ofNullable(props).map(PipelineStackProps::getProdFargateService).map(ApplicationLoadBalancedFargateService::getService).orElse(null))
                .blueGreenDeploymentConfig(EcsBlueGreenDeploymentConfig.builder()
                        .blueTargetGroup(Optional.ofNullable(props).map(PipelineStackProps::getProdFargateService).map(ApplicationLoadBalancedFargateService::getTargetGroup).orElse(null))
                        .greenTargetGroup(Optional.ofNullable(props).map(PipelineStackProps::getGreenTargetGroup).orElse(null))
                        .listener(Optional.ofNullable(props).map(PipelineStackProps::getProdFargateService).map(ApplicationLoadBalancedFargateService::getListener).orElse(null))
                        .testListener(Optional.ofNullable(props).map(PipelineStackProps::getGreenLoadBalancerListener).orElse(null))
                        .build())
                .deploymentConfig(EcsDeploymentConfig.LINEAR_10_PERCENT_EVERY_1_MINUTES)
                .application(ecsCodeDeployApp)
                .build());

        Artifact sourceOutput = new Artifact();
        Artifact unitTestOutput = new Artifact();
        Artifact dockerBuildOutput = new Artifact();

        pipeline.addStage(StageOptions.builder()
                .stageName("Source")
                .actions(List.of(
                        new CodeStarConnectionsSourceAction(CodeStarConnectionsSourceActionProps.builder()
                                .actionName("GitHub")
                                .owner("Jalius001")
                                .repo("workshop-aws-study")
                                .branch("main")
                                .connectionArn("arn:aws:codeconnections:us-east-1:354196887646:connection/19384364-e6c2-44dd-bbaa-883dfe8f6005")
                                .output(sourceOutput)
                                .build()
                        )))
                .build());
        pipeline.addStage(StageOptions.builder()
                .stageName("Code-Quality-Testing")
                .actions(List.of(
                        new CodeBuildAction(CodeBuildActionProps.builder()
                                .actionName("Unit-Test")
                                .project(codeBuild)
                                .input(sourceOutput)
                                .outputs(List.of(unitTestOutput))
                                .build()
                        )))
                .build());
        pipeline.addStage(StageOptions.builder()
                .stageName("Docker-Push-ECR")
                .actions(List.of(
                        new CodeBuildAction(CodeBuildActionProps.builder()
                                .actionName("Docker-Build")
                                .project(dockerBuild)
                                .input(sourceOutput)
                                .outputs(List.of(dockerBuildOutput))
                                .build()
                        )))
                .build());
        pipeline.addStage(StageOptions.builder()
                .stageName("Deploy-Test")
                .actions(List.of(
                        new EcsDeployAction(EcsDeployActionProps.builder()
                                .actionName("Deploy-Fargate-Test")
                                .service(Optional.ofNullable(props).map(PipelineStackProps::getTestFargateService).map(ApplicationLoadBalancedFargateService::getService).orElse(null))
                                .input(dockerBuildOutput)
                                .build()
                        )))
                .build());
        pipeline.addStage(StageOptions.builder()
                .stageName("Deploy-Production")
                .actions(List.of(
                        new ManualApprovalAction(ManualApprovalActionProps.builder()
                                .actionName("Approve-Deploy-Prod")
                                .runOrder(1)
                                .build()),
                        new CodeDeployEcsDeployAction(CodeDeployEcsDeployActionProps.builder()
                                .actionName("BlueGreen-deployECS")
                                .deploymentGroup(prodEcsDeploymentGroup)
                                .appSpecTemplateInput(sourceOutput)
                                .taskDefinitionTemplateInput(sourceOutput)
                                .runOrder(2)
                                .build()
                        )))
                .build());

        GraphWidget buildRate = new GraphWidget(GraphWidgetProps.builder()
                .title("Build Successes and Failures")
                .width(6)
                .height(6)
                .view(GraphWidgetView.PIE)
                .left(List.of(
                        new Metric(MetricProps.builder()
                                .namespace("AWS/CodeBuild")
                                .metricName("SucceededBuilds")
                                .statistic("sum")
                                .label("Succeeded Builds")
                                .period(Duration.days(30))
                                .build()),
                        new Metric(MetricProps.builder()
                                .namespace("AWS/CodeBuild")
                                .metricName("FailedBuilds")
                                .statistic("sum")
                                .label("Failed Builds")
                                .period(Duration.days(30))
                                .build())
                                ))
                .build());
        SingleValueWidget buildsCount = new SingleValueWidget(SingleValueWidgetProps.builder()
                .title("Total Builds")
                .width(6)
                .height(6)
                .metrics(List.of(
                        new Metric(MetricProps.builder()
                                .namespace("AWS/CodeBuild")
                                .metricName("Builds")
                                .statistic("sum")
                                .label("Builds")
                                .period(Duration.days(30))
                                .build())
                ))
                .build());
        GaugeWidget averageDuration = new GaugeWidget(GaugeWidgetProps.builder()
                .title("Average Build Time")
                .width(6)
                .height(6)
                .metrics(List.of(
                        new Metric(MetricProps.builder()
                                .namespace("AWS/CodeBuild")
                                .metricName("Duration")
                                .statistic("avg")
                                .label("Duration")
                                .period(Duration.hours(1))
                                .build())
                ))
                .leftYAxis(YAxisProps.builder()
                        .min(0)
                        .max(300)
                        .build())
                .build());
        GaugeWidget queuedDuration = new GaugeWidget(GaugeWidgetProps.builder()
                .title("Build Queue Duration")
                .width(6)
                .height(6)
                .metrics(List.of(
                        new Metric(MetricProps.builder()
                                .namespace("AWS/CodeBuild")
                                .metricName("QueuedDuration")
                                .statistic("avg")
                                .label("Duration")
                                .period(Duration.hours(1))
                                .build())
                ))
                .leftYAxis(YAxisProps.builder()
                        .min(0)
                        .max(60)
                        .build())
                .build());
        GraphWidget downloadDuration = new GraphWidget(GraphWidgetProps.builder()
                .title("Checkout Duration")
                .width(24)
                .height(5)
                .left(List.of(
                        new Metric(MetricProps.builder()
                                .namespace("AWS/CodeBuild")
                                .metricName("DownloadSourceDuration")
                                .statistic("max")
                                .label("Duration")
                                .period(Duration.minutes(5))
                                .color(Color.PURPLE)
                                .build())
                ))
                .build());

        new Dashboard(this, "CICD_Dashboard", DashboardProps.builder()
                .dashboardName("CICD_Dashboard")
                .widgets(List.of(List.of(buildRate, buildsCount, averageDuration, queuedDuration, downloadDuration)))
                .build());

        Topic failureTopic = new Topic(this, "BuildFailure", TopicProps.builder()
                .displayName("BuildFailure")
                .build());
        EmailSubscription emailSubscription = new EmailSubscription("s.rylskyy@gmail.com");
        failureTopic.addSubscription(emailSubscription);

        // CloudWatch event rule triggered on pipeline failures
        Rule pipelineFailureRule = new Rule(this, "PipelineFailureRule", RuleProps.builder()
                .description("Notify on pipeline failures")
                .eventPattern(EventPattern.builder()
                        .source(List.of("aws.codepipeline"))
                        .detailType(List.of("CodePipeline Pipeline Execution State Change"))
                        .detail(Map.of("state", List.of("FAILED")))
                        .build())
                .build());

        SnsTopic snsTopic = new SnsTopic(failureTopic, SnsTopicProps.builder()
                .message(RuleTargetInput.fromText(
                        format("Pipeline Failure Detected! Pipeline: %s, Execution ID: %s",
                                EventField.fromPath("$.detail.pipeline"),
                                EventField.fromPath("$.detail.execution-id"))))
                .build());
        pipelineFailureRule.addTarget(snsTopic);


        new CfnOutput(this, "SourceConnectionArn", CfnOutputProps.builder()
                .value(gitHubConnection.getAttrConnectionArn())
                .build());
        new CfnOutput(this, "SourceConnectionStatus", CfnOutputProps.builder()
                .value(gitHubConnection.getAttrConnectionStatus())
                .build());
    }
}
