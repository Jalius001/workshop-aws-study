import * as cdk from 'aws-cdk-lib';
import { AppCdkStack } from '../lib/app-cdk-stack';
import {PipelineCdkStack} from "../lib/pipeline-cdk-stack";
import 'source-map-support/register';
//#!/usr/bin/env node

const app = new cdk.App();

const testCdkStack = new AppCdkStack(app, 'test', {});
const pipelineCdkStack = new PipelineCdkStack(app, 'pipeline-stack', {});

//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