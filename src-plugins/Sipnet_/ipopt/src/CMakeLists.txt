cmake_minimum_required(VERSION 2.6)

set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)

# requirements
find_package(SWIG REQUIRED)
find_package(JNI REQUIRED)
find_package(IPOPT REQUIRED)
find_package(CoinHsl REQUIRED)

# SWIG interface creation
add_custom_command(OUTPUT
                     ${CMAKE_CURRENT_BINARY_DIR}/interface_wrap.cxx
                     ${CMAKE_CURRENT_BINARY_DIR}/ipopt.java
                     ${CMAKE_CURRENT_BINARY_DIR}/IpOpt.java ipoptJNI.java
                   DEPENDS
                     ${CMAKE_CURRENT_SOURCE_DIR}/interface.i
                   COMMAND
                     ${SWIG_EXECUTABLE}
                   ARGS
                     -c++ -java -package ipopt -outdir ${CMAKE_CURRENT_BINARY_DIR}
                     -o ${CMAKE_CURRENT_BINARY_DIR}/interface_wrap.cxx
                     ${CMAKE_CURRENT_SOURCE_DIR}/interface.i
                     )

SET(wrapper_lib_src interface.cxx interface_wrap.cxx)
include_directories(${CMAKE_CURRENT_SOURCE_DIR} ${IPOPT_INCLUDE_DIR} ${JNI_INCLUDE_DIRS})

# target
add_library(jipopt SHARED ${wrapper_lib_src})
target_link_libraries(jipopt ${IPOPT_LIBRARY} ${COINHSL_LIBRARY} blas lapack dl pthread)
