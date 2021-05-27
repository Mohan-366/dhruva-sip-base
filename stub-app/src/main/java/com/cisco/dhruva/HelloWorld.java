package com.cisco.dhruva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloWorld {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorld.class);
    public static void main(String[] args) {
        LOGGER.info("Hello world");
        System.out.println("Hello World");

    }
    public int returnZero(){
        return 0;
    }
}
