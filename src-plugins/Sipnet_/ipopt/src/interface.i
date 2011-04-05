%module ipopt
%include "arrays_java.i";
%{
#include <interface.h>
%}

class IpOpt {

public:

    IpOpt(int numNodes, int numConstraints);

    void setSingleSiteFactor(int node, double value0, double value1);

    void setEdgeFactor(int node1, int node2,
                       double value00, double value01,
                       double value10, double value11);

    void setFactor(int numNodes, int nodes[], double values[]);

    void setLinearConstraint(int numNodes, int nodes[],
                             double coefficients[],
                             int relation, double b);

    void inferMarginals(int numThreads);

    int getState(int node);

    double getMarginal(int node, int state);
};
