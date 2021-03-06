package org.antlr.v4.test.runtime.java;

import org.antlr.v4.test.runtime.BaseRuntimeTest;
import org.antlr.v4.test.runtime.RuntimeTestDescriptor;
import org.antlr.v4.test.runtime.descriptors.SetsDescriptors;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestSets extends BaseRuntimeTest {
	public TestSets(RuntimeTestDescriptor descriptor) {
		super(descriptor,new BaseJavaTest());
	}

	@Parameterized.Parameters(name="{0}")
	public static RuntimeTestDescriptor[] getAllTestDescriptors() {
		return BaseRuntimeTest.getRuntimeTestDescriptors(SetsDescriptors.class, "Java");
	}
}
