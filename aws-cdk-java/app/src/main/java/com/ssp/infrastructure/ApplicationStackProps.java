package com.ssp.infrastructure;

import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ecr.Repository;

public interface ApplicationStackProps extends StackProps {
    Repository getEcrRepository();

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private Repository ecrRepository;

        public Builder ecrRepository(Repository ecrRepository) {
            this.ecrRepository = ecrRepository;
            return this;
        }

        public ApplicationStackProps build() {
            return new ApplicationStackProps() {
                @Override
                public Repository getEcrRepository() {
                    return ecrRepository;
                }
            };
        }
    }
}
