package com.ssp.infrastructure;

import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;

public interface PipelineStackProps extends StackProps {
    Repository getEcrRepository();
    ApplicationLoadBalancedFargateService getTestFargateService();
    ApplicationTargetGroup getGreenTargetGroup();
    ApplicationListener getGreenLoadBalancerListener();
    ApplicationLoadBalancedFargateService getProdFargateService();

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private Repository ecrRepository;
        private ApplicationTargetGroup greenTargetGroup;
        private ApplicationListener greenLoadBalancerListener;
        private ApplicationLoadBalancedFargateService testFargateService;
        private ApplicationLoadBalancedFargateService prodFargateService;

        public Builder ecrRepository(Repository ecrRepository) {
            this.ecrRepository = ecrRepository;
            return this;
        }
        public Builder greenTargetGroup(ApplicationTargetGroup applicationTargetGroup) {
            this.greenTargetGroup = applicationTargetGroup;
            return this;
        }
        public Builder greenLoadBalancerListener(ApplicationListener applicationListener) {
            this.greenLoadBalancerListener = applicationListener;
            return this;
        }

        public Builder testFargateService(ApplicationLoadBalancedFargateService fargateService) {
            this.testFargateService = fargateService;
            return this;
        }
        public Builder prodFargateService(ApplicationLoadBalancedFargateService fargateService) {
            this.prodFargateService = fargateService;
            return this;
        }

        public PipelineStackProps build() {
            return new PipelineStackProps() {
                @Override
                public Repository getEcrRepository() {
                    return ecrRepository;
                }
                @Override
                public ApplicationTargetGroup getGreenTargetGroup() {
                    return greenTargetGroup;
                }
                @Override
                public ApplicationListener getGreenLoadBalancerListener() {
                    return greenLoadBalancerListener;
                }

                @Override
                public ApplicationLoadBalancedFargateService getTestFargateService() {
                    return testFargateService;
                }
                @Override
                public ApplicationLoadBalancedFargateService getProdFargateService() {
                    return prodFargateService;
                }
            };
        }
    }
}
