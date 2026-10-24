package com.example.demo.services;



// here we can implement the logic to check for idempotency of requests, for example by checking if a request with the same parameters has already been processed or processing.
// if it processed return the result.
// if it is processing return a message that the request is being processed.
// if it fails return a message that the request has failed and can be retried with the same parameters.
public class IdempotencyChecker {
    
}
