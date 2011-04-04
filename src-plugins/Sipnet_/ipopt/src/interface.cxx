#include <iostream>
#include "interface.h"

using namespace Ipopt;

IpOpt::IpOpt(size_t numNodes, size_t numConstraints) :
	_marginals(numNodes),
	_numVariables(numNodes),
	_numConstraints(numConstraints) {

	// initialize ipopt solver
}

void
IpOpt::setSingleSiteFactor(size_t node, double value0, double value1) {

}

void
IpOpt::setEdgeFactor(size_t node1, size_t node2,
										 double value00, double value01,
										 double value10, double value11) {

}

void
IpOpt::setFactor(int numNodes, size_t* nodes, double* values) {

	throw "[IpOpt] factors of degree > 2 are currently not supported";
}

void
IpOpt::setLinearConstraint(int numNodes, size_t* nodes, double* coefficients,
													 double lowerBound, double upperBound) {

}

void
IpOpt::inferMarginals(int numThreads) {

	// perform non-linear convex optimization
}

int
IpOpt::getState(size_t node) {

	if (_marginals[node][0] > _marginals[node][1])
		return 0;
	return 1;
}

double
IpOpt::getMarginal(size_t node, int state) {

	return _marginals[node][state];
}


/////////////////////////////
// IpOpt interface methods //
/////////////////////////////


// returns the size of the problem
bool IpOpt::get_nlp_info(
		int& n, int& m, int& nnz_jac_g,
		int& nnz_h_lag, IndexStyleEnum& index_style) {

	n = _numVariables;
	m = _numConstraints;

	// TODO: number of non-zero entries in the Jacobian of g
	nnz_jac_g = 8;

	// TODO: number of non-zero entries on lower left triangle (including
	// diagonal) of the Hessian of h
	nnz_h_lag = 10;

	// use the C style indexing (0-based)
	index_style = TNLP::C_STYLE;

	return true;
}

// returns the variable bounds
bool IpOpt::get_bounds_info(
		int n, double* x_l, double* x_u,
		int m, double* g_l, double* g_u) {

	// the variables are marginals, i.e., in [0,1]
	for (int i = 0; i < _numVariables; i++) {
		x_l[i] = 0.0;
		x_u[i] = 1.0;
	}

	// TODO: set constraint bounds
	for (int j = 0; j < _numConstraints; j++)
		g_l[j] = g_u[j] = 0.0;

	return true;
}

// returns the initial point for the problem
bool IpOpt::get_starting_point(
		int n, bool init_x, double* x,
		bool init_z, double* z_L, double* z_U,
		int m, bool init_lambda,
		double* lambda) {

	// TODO: find a feasible starting point

	x[0] = 1.0;
	x[1] = 5.0;
	x[2] = 5.0;
	x[3] = 1.0;

	return true;
}

// returns the value of the objective function
bool IpOpt::eval_f(int n, const double* x, bool new_x, double& obj_value) {

	// TODO: compute objective value
	obj_value = x[0] * x[3] * (x[0] + x[1] + x[2]) + x[2];

	return true;
}

// return the gradient of the objective function grad_{x} f(x)
bool IpOpt::eval_grad_f(int n, const double* x, bool new_x, double* grad_f) {

	// TODO: compute gradient of objective function
	grad_f[0] = x[0] * x[3] + x[3] * (x[0] + x[1] + x[2]);
	grad_f[1] = x[0] * x[3];
	grad_f[2] = x[0] * x[3] + 1;
	grad_f[3] = x[0] * (x[0] + x[1] + x[2]);

	return true;
}

// return the value of the constraints: g(x)
bool IpOpt::eval_g(int n, const double* x, bool new_x, int m, double* g) {
	// TODO: compute the constraint values

	g[0] = x[0] * x[1] * x[2] * x[3];
	g[1] = x[0]*x[0] + x[1]*x[1] + x[2]*x[2] + x[3]*x[3];

	return true;
}

// return the structure or values of the jacobian
bool IpOpt::eval_jac_g(
		int n, const double* x, bool new_x,
		int m, int nele_jac, int* iRow, int *jCol,
		double* values) {

	// TODO: compute the Jacobian of g

	if (values == NULL) {
		// return the structure of the jacobian

		// this particular jacobian is dense
		iRow[0] = 0;
		jCol[0] = 0;
		iRow[1] = 0;
		jCol[1] = 1;
		iRow[2] = 0;
		jCol[2] = 2;
		iRow[3] = 0;
		jCol[3] = 3;
		iRow[4] = 1;
		jCol[4] = 0;
		iRow[5] = 1;
		jCol[5] = 1;
		iRow[6] = 1;
		jCol[6] = 2;
		iRow[7] = 1;
		jCol[7] = 3;
	}
	else {
		// return the values of the jacobian of the constraints

		values[0] = x[1]*x[2]*x[3]; // 0,0
		values[1] = x[0]*x[2]*x[3]; // 0,1
		values[2] = x[0]*x[1]*x[3]; // 0,2
		values[3] = x[0]*x[1]*x[2]; // 0,3

		values[4] = 2*x[0]; // 1,0
		values[5] = 2*x[1]; // 1,1
		values[6] = 2*x[2]; // 1,2
		values[7] = 2*x[3]; // 1,3
	}

	return true;
}

//return the structure or values of the hessian
bool IpOpt::eval_h(
		int n, const double* x, bool new_x,
		double obj_factor, int m, const double* lambda,
		bool new_lambda, int nele_hess, int* iRow,
		int* jCol, double* values) {

	if (values == NULL) {
		// return the structure. This is a symmetric matrix, fill the lower left
		// triangle only.

		// the hessian for this problem is actually dense
		int idx=0;
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col <= row; col++) {
				iRow[idx] = row;
				jCol[idx] = col;
				idx++;
			}
		}

		assert(idx == nele_hess);
	}
	else {
		// return the values. This is a symmetric matrix, fill the lower left
		// triangle only

		// fill the objective portion
		values[0] = obj_factor * (2*x[3]); // 0,0

		values[1] = obj_factor * (x[3]);	 // 1,0
		values[2] = 0.;										// 1,1

		values[3] = obj_factor * (x[3]);	 // 2,0
		values[4] = 0.;										// 2,1
		values[5] = 0.;										// 2,2

		values[6] = obj_factor * (2*x[0] + x[1] + x[2]); // 3,0
		values[7] = obj_factor * (x[0]);								 // 3,1
		values[8] = obj_factor * (x[0]);								 // 3,2
		values[9] = 0.;																	// 3,3


		// add the portion for the first constraint
		values[1] += lambda[0] * (x[2] * x[3]); // 1,0

		values[3] += lambda[0] * (x[1] * x[3]); // 2,0
		values[4] += lambda[0] * (x[0] * x[3]); // 2,1

		values[6] += lambda[0] * (x[1] * x[2]); // 3,0
		values[7] += lambda[0] * (x[0] * x[2]); // 3,1
		values[8] += lambda[0] * (x[0] * x[1]); // 3,2

		// add the portion for the second constraint
		values[0] += lambda[1] * 2; // 0,0

		values[2] += lambda[1] * 2; // 1,1

		values[5] += lambda[1] * 2; // 2,2

		values[9] += lambda[1] * 2; // 3,3
	}

	return true;
}

void IpOpt::finalize_solution(
		SolverReturn status,
		int n, const double* x, const double* z_L, const double* z_U,
		int m, const double* g, const double* lambda,
		double obj_value,
		const IpoptData* ip_data,
		IpoptCalculatedQuantities* ip_cq) {

	// TODO: store solution x in _marginals
}
