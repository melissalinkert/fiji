%module ipopt
%include "arrays_java.i";
%{
#include <interface.h>
%}

typedef unsigned int size_t;

class IpOpt {

public:

    IpOpt(size_t numNodes, size_t numConstraints);

    void setSingleSiteFactor(size_t node, double value0, double value1);

    void setEdgeFactor(size_t node1, size_t node2,
                       double value00, double value01,
                       double value10, double value11);

    void setFactor(int numNodes, size_t nodes[], double values[]);

    void setLinearConstraint(int numNodes, size_t nodes[],
                             double coefficients[],
                             int relation, double b);

    void inferMarginals(int numThreads);

    int getState(size_t node);

    double getMarginal(size_t node, int state);
};
