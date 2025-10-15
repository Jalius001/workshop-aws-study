package com.ssp.infrastructure;

import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codeconnections.CfnConnection;
import software.amazon.awscdk.services.codeconnections.CfnConnectionProps;
import software.amazon.awscdk.services.codedeploy.*;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.ssm.StringParameterProps;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
        environmentVariables.put("IMAGE_REPO_URI", BuildEnvironmentVariable.builder().type(BuildEnvironmentVariableType.PLAINTEXT).value(props.getEcrRepository().getRepositoryUri()).build());
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
                .service(props.getProdFargateService().getService())
                .blueGreenDeploymentConfig(EcsBlueGreenDeploymentConfig.builder()
                        .blueTargetGroup(props.getProdFargateService().getTargetGroup())
                        .greenTargetGroup(props.getGreenTargetGroup())
                        .listener(props.getProdFargateService().getListener())
                        .testListener(props.getGreenLoadBalancerListener())
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
                                .service(props.getTestFargateService().getService())
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

        new CfnOutput(this, "SourceConnectionArn", CfnOutputProps.builder()
                .value(gitHubConnection.getAttrConnectionArn())
                .build());
        new CfnOutput(this, "SourceConnectionStatus", CfnOutputProps.builder()
                .value(gitHubConnection.getAttrConnectionStatus())
                .build());
    }
}
