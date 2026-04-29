package example.grpcclient;

import io.grpc.stub.StreamObserver;
import services.ConverterGrpc;
import services.ConverterOuterClass.TempReply;
import services.ConverterOuterClass.TempRequest;

public class ConverterImpl extends ConverterGrpc.ConverterImplBase {
    @Override
    public void celsiusToFahrenheit(TempRequest request, StreamObserver<TempReply> responseObserver) {
        double result = request.getValue() * 9 / 5 + 32;
        responseObserver.onNext(TempReply.newBuilder().setValue(result).build());
        responseObserver.onCompleted();
    }

    @Override
    public void fahrenheitToCelsius(TempRequest request, StreamObserver<TempReply> responseObserver) {
        double result = (request.getValue() - 32) * 5 / 9;
        responseObserver.onNext(TempReply.newBuilder().setValue(result).build());
        responseObserver.onCompleted();
    }
}
