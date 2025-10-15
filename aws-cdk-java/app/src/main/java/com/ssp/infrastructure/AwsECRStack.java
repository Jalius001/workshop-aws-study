package com.ssp.infrastructure;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.constructs.Construct;

public class AwsECRStack extends Stack {

    @Getter
    private Repository repository;

    public AwsECRStack(@Nullable Construct scope, @Nullable String id) {
        super(scope, id);

        this.repository = new Repository(this, "my_app");
    }
}
