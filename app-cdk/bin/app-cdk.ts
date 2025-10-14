import * as cdk from 'aws-cdk-lib';
import { AppCdkStack } from '../lib/app-cdk-stack';
import {PipelineCdkStack} from "../lib/pipeline-cdk-stack";
import 'source-map-support/register';
import { EcrCdkStack } from '../lib/ecr-cdk-stack';
//#!/usr/bin/env node

const app = new cdk.App();

const ecrCdkStack = new EcrCdkStack(app, 'ecr-stack', {});
const testCdkStack = new AppCdkStack(app, 'test', {
    ecrRepository: ecrCdkStack.repository,
});
const prodCdkStack = new AppCdkStack(app, 'prod', {
    ecrRepository: ecrCdkStack.repository,
});const pipelineCdkStack = new PipelineCdkStack(app, 'pipeline-stack', {
    ecrRepository: ecrCdkStack.repository,
    fargateServiceTest: testCdkStack.fargateService,
    fargateServiceProd: prodCdkStack.fargateService,
});

//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