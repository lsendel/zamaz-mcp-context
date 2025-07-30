package com.contextengine.mcp.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Main Context Engine Service
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.60.1)",
    comments = "Source: context_engine.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ContextEngineServiceGrpc {

  private ContextEngineServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "com.contextengine.mcp.proto.ContextEngineService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.contextengine.mcp.proto.SearchCodeRequest,
      com.contextengine.mcp.proto.SearchCodeResponse> getSearchCodeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SearchCode",
      requestType = com.contextengine.mcp.proto.SearchCodeRequest.class,
      responseType = com.contextengine.mcp.proto.SearchCodeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.contextengine.mcp.proto.SearchCodeRequest,
      com.contextengine.mcp.proto.SearchCodeResponse> getSearchCodeMethod() {
    io.grpc.MethodDescriptor<com.contextengine.mcp.proto.SearchCodeRequest, com.contextengine.mcp.proto.SearchCodeResponse> getSearchCodeMethod;
    if ((getSearchCodeMethod = ContextEngineServiceGrpc.getSearchCodeMethod) == null) {
      synchronized (ContextEngineServiceGrpc.class) {
        if ((getSearchCodeMethod = ContextEngineServiceGrpc.getSearchCodeMethod) == null) {
          ContextEngineServiceGrpc.getSearchCodeMethod = getSearchCodeMethod =
              io.grpc.MethodDescriptor.<com.contextengine.mcp.proto.SearchCodeRequest, com.contextengine.mcp.proto.SearchCodeResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SearchCode"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.SearchCodeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.SearchCodeResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ContextEngineServiceMethodDescriptorSupplier("SearchCode"))
              .build();
        }
      }
    }
    return getSearchCodeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.contextengine.mcp.proto.OptimizeContextRequest,
      com.contextengine.mcp.proto.OptimizeContextResponse> getOptimizeContextMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "OptimizeContext",
      requestType = com.contextengine.mcp.proto.OptimizeContextRequest.class,
      responseType = com.contextengine.mcp.proto.OptimizeContextResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.contextengine.mcp.proto.OptimizeContextRequest,
      com.contextengine.mcp.proto.OptimizeContextResponse> getOptimizeContextMethod() {
    io.grpc.MethodDescriptor<com.contextengine.mcp.proto.OptimizeContextRequest, com.contextengine.mcp.proto.OptimizeContextResponse> getOptimizeContextMethod;
    if ((getOptimizeContextMethod = ContextEngineServiceGrpc.getOptimizeContextMethod) == null) {
      synchronized (ContextEngineServiceGrpc.class) {
        if ((getOptimizeContextMethod = ContextEngineServiceGrpc.getOptimizeContextMethod) == null) {
          ContextEngineServiceGrpc.getOptimizeContextMethod = getOptimizeContextMethod =
              io.grpc.MethodDescriptor.<com.contextengine.mcp.proto.OptimizeContextRequest, com.contextengine.mcp.proto.OptimizeContextResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "OptimizeContext"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.OptimizeContextRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.OptimizeContextResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ContextEngineServiceMethodDescriptorSupplier("OptimizeContext"))
              .build();
        }
      }
    }
    return getOptimizeContextMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.contextengine.mcp.proto.SwitchModelRequest,
      com.contextengine.mcp.proto.SwitchModelResponse> getSwitchModelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SwitchModel",
      requestType = com.contextengine.mcp.proto.SwitchModelRequest.class,
      responseType = com.contextengine.mcp.proto.SwitchModelResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.contextengine.mcp.proto.SwitchModelRequest,
      com.contextengine.mcp.proto.SwitchModelResponse> getSwitchModelMethod() {
    io.grpc.MethodDescriptor<com.contextengine.mcp.proto.SwitchModelRequest, com.contextengine.mcp.proto.SwitchModelResponse> getSwitchModelMethod;
    if ((getSwitchModelMethod = ContextEngineServiceGrpc.getSwitchModelMethod) == null) {
      synchronized (ContextEngineServiceGrpc.class) {
        if ((getSwitchModelMethod = ContextEngineServiceGrpc.getSwitchModelMethod) == null) {
          ContextEngineServiceGrpc.getSwitchModelMethod = getSwitchModelMethod =
              io.grpc.MethodDescriptor.<com.contextengine.mcp.proto.SwitchModelRequest, com.contextengine.mcp.proto.SwitchModelResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SwitchModel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.SwitchModelRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.SwitchModelResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ContextEngineServiceMethodDescriptorSupplier("SwitchModel"))
              .build();
        }
      }
    }
    return getSwitchModelMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.contextengine.mcp.proto.GetProjectMemoryRequest,
      com.contextengine.mcp.proto.GetProjectMemoryResponse> getGetProjectMemoryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetProjectMemory",
      requestType = com.contextengine.mcp.proto.GetProjectMemoryRequest.class,
      responseType = com.contextengine.mcp.proto.GetProjectMemoryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.contextengine.mcp.proto.GetProjectMemoryRequest,
      com.contextengine.mcp.proto.GetProjectMemoryResponse> getGetProjectMemoryMethod() {
    io.grpc.MethodDescriptor<com.contextengine.mcp.proto.GetProjectMemoryRequest, com.contextengine.mcp.proto.GetProjectMemoryResponse> getGetProjectMemoryMethod;
    if ((getGetProjectMemoryMethod = ContextEngineServiceGrpc.getGetProjectMemoryMethod) == null) {
      synchronized (ContextEngineServiceGrpc.class) {
        if ((getGetProjectMemoryMethod = ContextEngineServiceGrpc.getGetProjectMemoryMethod) == null) {
          ContextEngineServiceGrpc.getGetProjectMemoryMethod = getGetProjectMemoryMethod =
              io.grpc.MethodDescriptor.<com.contextengine.mcp.proto.GetProjectMemoryRequest, com.contextengine.mcp.proto.GetProjectMemoryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetProjectMemory"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.GetProjectMemoryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.GetProjectMemoryResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ContextEngineServiceMethodDescriptorSupplier("GetProjectMemory"))
              .build();
        }
      }
    }
    return getGetProjectMemoryMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.contextengine.mcp.proto.AnalyzeCodebaseRequest,
      com.contextengine.mcp.proto.AnalyzeCodebaseResponse> getAnalyzeCodebaseMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AnalyzeCodebase",
      requestType = com.contextengine.mcp.proto.AnalyzeCodebaseRequest.class,
      responseType = com.contextengine.mcp.proto.AnalyzeCodebaseResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.contextengine.mcp.proto.AnalyzeCodebaseRequest,
      com.contextengine.mcp.proto.AnalyzeCodebaseResponse> getAnalyzeCodebaseMethod() {
    io.grpc.MethodDescriptor<com.contextengine.mcp.proto.AnalyzeCodebaseRequest, com.contextengine.mcp.proto.AnalyzeCodebaseResponse> getAnalyzeCodebaseMethod;
    if ((getAnalyzeCodebaseMethod = ContextEngineServiceGrpc.getAnalyzeCodebaseMethod) == null) {
      synchronized (ContextEngineServiceGrpc.class) {
        if ((getAnalyzeCodebaseMethod = ContextEngineServiceGrpc.getAnalyzeCodebaseMethod) == null) {
          ContextEngineServiceGrpc.getAnalyzeCodebaseMethod = getAnalyzeCodebaseMethod =
              io.grpc.MethodDescriptor.<com.contextengine.mcp.proto.AnalyzeCodebaseRequest, com.contextengine.mcp.proto.AnalyzeCodebaseResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AnalyzeCodebase"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.AnalyzeCodebaseRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.AnalyzeCodebaseResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ContextEngineServiceMethodDescriptorSupplier("AnalyzeCodebase"))
              .build();
        }
      }
    }
    return getAnalyzeCodebaseMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.contextengine.mcp.proto.BatchProcessRequest,
      com.contextengine.mcp.proto.BatchProcessResponse> getBatchProcessMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "BatchProcess",
      requestType = com.contextengine.mcp.proto.BatchProcessRequest.class,
      responseType = com.contextengine.mcp.proto.BatchProcessResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.contextengine.mcp.proto.BatchProcessRequest,
      com.contextengine.mcp.proto.BatchProcessResponse> getBatchProcessMethod() {
    io.grpc.MethodDescriptor<com.contextengine.mcp.proto.BatchProcessRequest, com.contextengine.mcp.proto.BatchProcessResponse> getBatchProcessMethod;
    if ((getBatchProcessMethod = ContextEngineServiceGrpc.getBatchProcessMethod) == null) {
      synchronized (ContextEngineServiceGrpc.class) {
        if ((getBatchProcessMethod = ContextEngineServiceGrpc.getBatchProcessMethod) == null) {
          ContextEngineServiceGrpc.getBatchProcessMethod = getBatchProcessMethod =
              io.grpc.MethodDescriptor.<com.contextengine.mcp.proto.BatchProcessRequest, com.contextengine.mcp.proto.BatchProcessResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "BatchProcess"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.BatchProcessRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.BatchProcessResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ContextEngineServiceMethodDescriptorSupplier("BatchProcess"))
              .build();
        }
      }
    }
    return getBatchProcessMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.contextengine.mcp.proto.StreamContextRequest,
      com.contextengine.mcp.proto.StreamContextResponse> getStreamOptimizedContextMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamOptimizedContext",
      requestType = com.contextengine.mcp.proto.StreamContextRequest.class,
      responseType = com.contextengine.mcp.proto.StreamContextResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.contextengine.mcp.proto.StreamContextRequest,
      com.contextengine.mcp.proto.StreamContextResponse> getStreamOptimizedContextMethod() {
    io.grpc.MethodDescriptor<com.contextengine.mcp.proto.StreamContextRequest, com.contextengine.mcp.proto.StreamContextResponse> getStreamOptimizedContextMethod;
    if ((getStreamOptimizedContextMethod = ContextEngineServiceGrpc.getStreamOptimizedContextMethod) == null) {
      synchronized (ContextEngineServiceGrpc.class) {
        if ((getStreamOptimizedContextMethod = ContextEngineServiceGrpc.getStreamOptimizedContextMethod) == null) {
          ContextEngineServiceGrpc.getStreamOptimizedContextMethod = getStreamOptimizedContextMethod =
              io.grpc.MethodDescriptor.<com.contextengine.mcp.proto.StreamContextRequest, com.contextengine.mcp.proto.StreamContextResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamOptimizedContext"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.StreamContextRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.contextengine.mcp.proto.StreamContextResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ContextEngineServiceMethodDescriptorSupplier("StreamOptimizedContext"))
              .build();
        }
      }
    }
    return getStreamOptimizedContextMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ContextEngineServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ContextEngineServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ContextEngineServiceStub>() {
        @java.lang.Override
        public ContextEngineServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ContextEngineServiceStub(channel, callOptions);
        }
      };
    return ContextEngineServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ContextEngineServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ContextEngineServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ContextEngineServiceBlockingStub>() {
        @java.lang.Override
        public ContextEngineServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ContextEngineServiceBlockingStub(channel, callOptions);
        }
      };
    return ContextEngineServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ContextEngineServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ContextEngineServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ContextEngineServiceFutureStub>() {
        @java.lang.Override
        public ContextEngineServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ContextEngineServiceFutureStub(channel, callOptions);
        }
      };
    return ContextEngineServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * Main Context Engine Service
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Search code semantically
     * </pre>
     */
    default void searchCode(com.contextengine.mcp.proto.SearchCodeRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.SearchCodeResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSearchCodeMethod(), responseObserver);
    }

    /**
     * <pre>
     * Optimize file context
     * </pre>
     */
    default void optimizeContext(com.contextengine.mcp.proto.OptimizeContextRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.OptimizeContextResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getOptimizeContextMethod(), responseObserver);
    }

    /**
     * <pre>
     * Switch LLM model for operations
     * </pre>
     */
    default void switchModel(com.contextengine.mcp.proto.SwitchModelRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.SwitchModelResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSwitchModelMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get project memory and insights
     * </pre>
     */
    default void getProjectMemory(com.contextengine.mcp.proto.GetProjectMemoryRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.GetProjectMemoryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetProjectMemoryMethod(), responseObserver);
    }

    /**
     * <pre>
     * Analyze entire codebase
     * </pre>
     */
    default void analyzeCodebase(com.contextengine.mcp.proto.AnalyzeCodebaseRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.AnalyzeCodebaseResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getAnalyzeCodebaseMethod(), responseObserver);
    }

    /**
     * <pre>
     * Batch process multiple files
     * </pre>
     */
    default void batchProcess(com.contextengine.mcp.proto.BatchProcessRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.BatchProcessResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getBatchProcessMethod(), responseObserver);
    }

    /**
     * <pre>
     * Stream optimized context (for large files)
     * </pre>
     */
    default void streamOptimizedContext(com.contextengine.mcp.proto.StreamContextRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.StreamContextResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStreamOptimizedContextMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ContextEngineService.
   * <pre>
   * Main Context Engine Service
   * </pre>
   */
  public static abstract class ContextEngineServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ContextEngineServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ContextEngineService.
   * <pre>
   * Main Context Engine Service
   * </pre>
   */
  public static final class ContextEngineServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ContextEngineServiceStub> {
    private ContextEngineServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ContextEngineServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ContextEngineServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Search code semantically
     * </pre>
     */
    public void searchCode(com.contextengine.mcp.proto.SearchCodeRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.SearchCodeResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSearchCodeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Optimize file context
     * </pre>
     */
    public void optimizeContext(com.contextengine.mcp.proto.OptimizeContextRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.OptimizeContextResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getOptimizeContextMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Switch LLM model for operations
     * </pre>
     */
    public void switchModel(com.contextengine.mcp.proto.SwitchModelRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.SwitchModelResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSwitchModelMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get project memory and insights
     * </pre>
     */
    public void getProjectMemory(com.contextengine.mcp.proto.GetProjectMemoryRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.GetProjectMemoryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetProjectMemoryMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Analyze entire codebase
     * </pre>
     */
    public void analyzeCodebase(com.contextengine.mcp.proto.AnalyzeCodebaseRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.AnalyzeCodebaseResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getAnalyzeCodebaseMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Batch process multiple files
     * </pre>
     */
    public void batchProcess(com.contextengine.mcp.proto.BatchProcessRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.BatchProcessResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getBatchProcessMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Stream optimized context (for large files)
     * </pre>
     */
    public void streamOptimizedContext(com.contextengine.mcp.proto.StreamContextRequest request,
        io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.StreamContextResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getStreamOptimizedContextMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ContextEngineService.
   * <pre>
   * Main Context Engine Service
   * </pre>
   */
  public static final class ContextEngineServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ContextEngineServiceBlockingStub> {
    private ContextEngineServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ContextEngineServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ContextEngineServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Search code semantically
     * </pre>
     */
    public com.contextengine.mcp.proto.SearchCodeResponse searchCode(com.contextengine.mcp.proto.SearchCodeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSearchCodeMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Optimize file context
     * </pre>
     */
    public com.contextengine.mcp.proto.OptimizeContextResponse optimizeContext(com.contextengine.mcp.proto.OptimizeContextRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getOptimizeContextMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Switch LLM model for operations
     * </pre>
     */
    public com.contextengine.mcp.proto.SwitchModelResponse switchModel(com.contextengine.mcp.proto.SwitchModelRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSwitchModelMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get project memory and insights
     * </pre>
     */
    public com.contextengine.mcp.proto.GetProjectMemoryResponse getProjectMemory(com.contextengine.mcp.proto.GetProjectMemoryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetProjectMemoryMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Analyze entire codebase
     * </pre>
     */
    public com.contextengine.mcp.proto.AnalyzeCodebaseResponse analyzeCodebase(com.contextengine.mcp.proto.AnalyzeCodebaseRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getAnalyzeCodebaseMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Batch process multiple files
     * </pre>
     */
    public com.contextengine.mcp.proto.BatchProcessResponse batchProcess(com.contextengine.mcp.proto.BatchProcessRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getBatchProcessMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Stream optimized context (for large files)
     * </pre>
     */
    public java.util.Iterator<com.contextengine.mcp.proto.StreamContextResponse> streamOptimizedContext(
        com.contextengine.mcp.proto.StreamContextRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getStreamOptimizedContextMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ContextEngineService.
   * <pre>
   * Main Context Engine Service
   * </pre>
   */
  public static final class ContextEngineServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ContextEngineServiceFutureStub> {
    private ContextEngineServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ContextEngineServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ContextEngineServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Search code semantically
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.contextengine.mcp.proto.SearchCodeResponse> searchCode(
        com.contextengine.mcp.proto.SearchCodeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSearchCodeMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Optimize file context
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.contextengine.mcp.proto.OptimizeContextResponse> optimizeContext(
        com.contextengine.mcp.proto.OptimizeContextRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getOptimizeContextMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Switch LLM model for operations
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.contextengine.mcp.proto.SwitchModelResponse> switchModel(
        com.contextengine.mcp.proto.SwitchModelRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSwitchModelMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get project memory and insights
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.contextengine.mcp.proto.GetProjectMemoryResponse> getProjectMemory(
        com.contextengine.mcp.proto.GetProjectMemoryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetProjectMemoryMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Analyze entire codebase
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.contextengine.mcp.proto.AnalyzeCodebaseResponse> analyzeCodebase(
        com.contextengine.mcp.proto.AnalyzeCodebaseRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getAnalyzeCodebaseMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Batch process multiple files
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.contextengine.mcp.proto.BatchProcessResponse> batchProcess(
        com.contextengine.mcp.proto.BatchProcessRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getBatchProcessMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SEARCH_CODE = 0;
  private static final int METHODID_OPTIMIZE_CONTEXT = 1;
  private static final int METHODID_SWITCH_MODEL = 2;
  private static final int METHODID_GET_PROJECT_MEMORY = 3;
  private static final int METHODID_ANALYZE_CODEBASE = 4;
  private static final int METHODID_BATCH_PROCESS = 5;
  private static final int METHODID_STREAM_OPTIMIZED_CONTEXT = 6;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SEARCH_CODE:
          serviceImpl.searchCode((com.contextengine.mcp.proto.SearchCodeRequest) request,
              (io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.SearchCodeResponse>) responseObserver);
          break;
        case METHODID_OPTIMIZE_CONTEXT:
          serviceImpl.optimizeContext((com.contextengine.mcp.proto.OptimizeContextRequest) request,
              (io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.OptimizeContextResponse>) responseObserver);
          break;
        case METHODID_SWITCH_MODEL:
          serviceImpl.switchModel((com.contextengine.mcp.proto.SwitchModelRequest) request,
              (io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.SwitchModelResponse>) responseObserver);
          break;
        case METHODID_GET_PROJECT_MEMORY:
          serviceImpl.getProjectMemory((com.contextengine.mcp.proto.GetProjectMemoryRequest) request,
              (io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.GetProjectMemoryResponse>) responseObserver);
          break;
        case METHODID_ANALYZE_CODEBASE:
          serviceImpl.analyzeCodebase((com.contextengine.mcp.proto.AnalyzeCodebaseRequest) request,
              (io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.AnalyzeCodebaseResponse>) responseObserver);
          break;
        case METHODID_BATCH_PROCESS:
          serviceImpl.batchProcess((com.contextengine.mcp.proto.BatchProcessRequest) request,
              (io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.BatchProcessResponse>) responseObserver);
          break;
        case METHODID_STREAM_OPTIMIZED_CONTEXT:
          serviceImpl.streamOptimizedContext((com.contextengine.mcp.proto.StreamContextRequest) request,
              (io.grpc.stub.StreamObserver<com.contextengine.mcp.proto.StreamContextResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSearchCodeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.contextengine.mcp.proto.SearchCodeRequest,
              com.contextengine.mcp.proto.SearchCodeResponse>(
                service, METHODID_SEARCH_CODE)))
        .addMethod(
          getOptimizeContextMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.contextengine.mcp.proto.OptimizeContextRequest,
              com.contextengine.mcp.proto.OptimizeContextResponse>(
                service, METHODID_OPTIMIZE_CONTEXT)))
        .addMethod(
          getSwitchModelMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.contextengine.mcp.proto.SwitchModelRequest,
              com.contextengine.mcp.proto.SwitchModelResponse>(
                service, METHODID_SWITCH_MODEL)))
        .addMethod(
          getGetProjectMemoryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.contextengine.mcp.proto.GetProjectMemoryRequest,
              com.contextengine.mcp.proto.GetProjectMemoryResponse>(
                service, METHODID_GET_PROJECT_MEMORY)))
        .addMethod(
          getAnalyzeCodebaseMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.contextengine.mcp.proto.AnalyzeCodebaseRequest,
              com.contextengine.mcp.proto.AnalyzeCodebaseResponse>(
                service, METHODID_ANALYZE_CODEBASE)))
        .addMethod(
          getBatchProcessMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.contextengine.mcp.proto.BatchProcessRequest,
              com.contextengine.mcp.proto.BatchProcessResponse>(
                service, METHODID_BATCH_PROCESS)))
        .addMethod(
          getStreamOptimizedContextMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.contextengine.mcp.proto.StreamContextRequest,
              com.contextengine.mcp.proto.StreamContextResponse>(
                service, METHODID_STREAM_OPTIMIZED_CONTEXT)))
        .build();
  }

  private static abstract class ContextEngineServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ContextEngineServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.contextengine.mcp.proto.ContextEngineProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ContextEngineService");
    }
  }

  private static final class ContextEngineServiceFileDescriptorSupplier
      extends ContextEngineServiceBaseDescriptorSupplier {
    ContextEngineServiceFileDescriptorSupplier() {}
  }

  private static final class ContextEngineServiceMethodDescriptorSupplier
      extends ContextEngineServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ContextEngineServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ContextEngineServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ContextEngineServiceFileDescriptorSupplier())
              .addMethod(getSearchCodeMethod())
              .addMethod(getOptimizeContextMethod())
              .addMethod(getSwitchModelMethod())
              .addMethod(getGetProjectMemoryMethod())
              .addMethod(getAnalyzeCodebaseMethod())
              .addMethod(getBatchProcessMethod())
              .addMethod(getStreamOptimizedContextMethod())
              .build();
        }
      }
    }
    return result;
  }
}
