package cn.bdqfork.core.proxy;

import cn.bdqfork.core.aop.*;
import cn.bdqfork.core.aop.aspect.AspectAdvice;
import cn.bdqfork.core.container.UnSharedInstance;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author bdq
 * @since 2019-07-31
 */
public abstract class AbstractAopInvocationHandler implements AdviceInvocationHandler {
    /**
     * 目标对象
     */
    protected Object target;
    /**
     * 代理接口
     */
    protected Class[] interfaces;
    /**
     * 切面
     */
    private List<Advisor> advisors;

    @Override
    public Object invoke(Method method, Object[] args) throws Throwable {
        Object targetObject = target;
        if (target.getClass() == UnSharedInstance.class) {
            UnSharedInstance unSharedInstance = (UnSharedInstance) target;
            targetObject = unSharedInstance.getObjectFactory().getObject();
        }
        if ("toString".equals(method.getName())) {
            return targetObject.toString();
        }
        if ("equals".equals(method.getName())) {
            return targetObject.equals(args[0]);
        }
        if ("hashCode".equals(method.getName())) {
            return targetObject.hashCode();
        }
        return invokeWithAdvice(targetObject, method, args);
    }

    private Object invokeWithAdvice(Object target, Method method, Object[] args) throws Throwable {
        MethodBeforeAdvice[] beforeAdvices = getMethodBeforeAdvices(method);
        MethodInvocation invocation = new MethodInvocation(target, method, args, beforeAdvices);
        MethodInterceptor[] aroundAdvices = getAroundAdvices(method);

        if (aroundAdvices.length > 0) {
            for (MethodInterceptor aroundAdvice : aroundAdvices) {
                try {
                    Object returnValue = doAround(aroundAdvice, invocation);
                    doAfterReturning(invocation, returnValue);
                    return returnValue;
                } catch (Exception e) {
                    doThrows(invocation, e);
                }
            }
        } else {
            try {
                Object returnValue = invocation.proceed();
                doAfterReturning(invocation, returnValue);
                return returnValue;
            } catch (Exception e) {
                doThrows(invocation, e);
            }
        }
        return null;
    }

    private MethodInterceptor[] getAroundAdvices(Method method) {
        return advisors.stream()
                .filter(advisor -> advisor.isMatch(method, MethodInterceptor.class))
                .map(advisor -> (MethodInterceptor) advisor.getAdvice())
                .toArray(MethodInterceptor[]::new);
    }

    private MethodBeforeAdvice[] getMethodBeforeAdvices(Method method) {
        return advisors.stream()
                .filter(advisor -> advisor.isMatch(method, MethodBeforeAdvice.class))
                .map(advisor -> (MethodBeforeAdvice) advisor.getAdvice())
                .toArray(MethodBeforeAdvice[]::new);
    }


    private Object doAround(MethodInterceptor methodInterceptor, MethodInvocation methodInvocation) throws Throwable {
        return methodInterceptor.invoke(methodInvocation);
    }

    private void doAfterReturning(MethodInvocation methodInvocation, Object returnValue) throws Throwable {
        for (Advisor advisor : advisors) {
            if (advisor.isMatch(methodInvocation.getMethod(), AfterReturningAdvice.class)) {
                AfterReturningAdvice advice = (AfterReturningAdvice) advisor.getAdvice();
                if (advice instanceof AspectAdvice) {
                    ((AspectAdvice) advice).setJoinPoint(methodInvocation);
                }
                advice.afterReturning(returnValue, methodInvocation.getMethod(),
                        methodInvocation.getArgs(), methodInvocation.getTarget());
            }
        }
    }

    private void doThrows(MethodInvocation methodInvocation, Exception e) throws Exception {
        long count = advisors.stream()
                .filter(advisor -> advisor.isMatch(methodInvocation.getMethod(), ThrowsAdvice.class))
                .count();
        if (count > 0) {
            advisors.stream()
                    .filter(advisor -> advisor.isMatch(methodInvocation.getMethod(), ThrowsAdvice.class))
                    .map(advisor -> (ThrowsAdvice) advisor.getAdvice())
                    .forEach(advice -> advice.afterThrowing(methodInvocation.getMethod(), methodInvocation.getArgs(),
                            methodInvocation.getTarget(), e));
        } else {
            throw e;
        }
    }

    @Override
    public void setAdvisors(List<Advisor> advisors) {
        this.advisors = advisors;
    }

    @Override
    public void setTarget(Object target) {
        this.target = target;
    }

    @Override
    public void setInterfaces(Class<?>... interfaces) {
        this.interfaces = interfaces;
    }
}