#include <iostream>
#include <limits>
#include <cmath>
#include "interface.h"

using namespace Ipopt;

IpOpt::IpOpt(int numNodes, int numConstraints) :
	_theta(numNodes),
	_marginals(numNodes),
	_numVariables(numNodes),
	_numConstraints(numConstraints),
	_nextConstraint(0),
	_numEntriesA(0),
	_constTerm(0.0) {

	// initialize ipopt solver
}

void
IpOpt::setSingleSiteFactor(int node, double value0, double value1) {

	double min = std::min(value0, value1);

	_theta[node] = (value1 - min) - (value0 - min);

	_constTerm += min;
}

void
IpOpt::setEdgeFactor(
		int node1, int node2,
		double value00, double value01,
		double value10, double value11) {

	throw "[IpOpt] factors of degree > 1 are currently not supported";
}

void
IpOpt::setFactor(int numNodes, int* nodes, double* values) {

	throw "[IpOpt] factors of degree > 1 are currently not supported";
}

void
IpOpt::setLinearConstraint(
		int numNodes, int* nodes, double* coefficients,
		int relation, double value) {

	std::vector<int>    vars(numNodes);
	std::vector<double> coefs(numNodes);

	for (int i = 0; i < numNodes; i++) {
		vars[i]  = nodes[i];
		coefs[i] = coefficients[i];
	}

	Constraint constraint = {vars, coefs, relation, value};
	_constraints.push_back(constraint);

	_numEntriesA += numNodes;
}

void
IpOpt::inferMarginals(int numThreads) {

	SmartPtr<IpoptApplication> app = IpoptApplicationFactory();

	app->Options()->SetStringValue("linear_solver", "mumps");
	app->Options()->SetIntegerValue("mumps_mem_percent", 5);

	ApplicationReturnStatus status;
	status = app->Initialize();
	if (status != Solve_Succeeded) {
		std::cout << "[IpOpt] Error during initialization!" << std::endl;
		return;
	}

	std::cout << "[IpOpt] solving non-linear constrained optimization problem..." << std::endl;
	status = app->OptimizeTNLP(this);

	if (status == Solve_Succeeded) {
		std::cout << "[IpOpt] done." << std::endl;
	}
	else {
		std::cout << "[IpOpt] an error occurred" << std::endl;
	}
}

int
IpOpt::getState(int node) {

	if (_marginals[node][0] > _marginals[node][1])
		return 0;
	return 1;
}

double
IpOpt::getMarginal(int node, int state) {

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

	// number of non-zero entries in the Jacobian of g (which is A)
	nnz_jac_g = _numEntriesA;

	// Number of non-zero entries on lower left triangle (including
	// diagonal) of the Hessian of h.
	// Our Hessian does only have diagonal entries.
	nnz_h_lag = _numVariables;

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

	// set constraint bounds
	for (int j = 0; j < _numConstraints; j++)
		if (_constraints[j].relation == 0)
			g_l[j] = g_u[j] = _constraints[j].value;
		else if (_constraints[j].relation == -1) {
			g_l[j] = -std::numeric_limits<double>::max();
			g_u[j] = _constraints[j].value;
		} else if (_constraints[j].relation == 1) {
			g_l[j] = _constraints[j].value;
			g_u[j] = std::numeric_limits<double>::max();
		}

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

	for (int i = 0; i < _numVariables; i++)
		obj_value +=
				// linear part
				_theta[i]*x[i]
				// entropy part
				-x[i]*log(x[i])
				-(1.0-x[i])*log(1.0-x[i]);

	return true;
}

// return the gradient of the objective function
bool IpOpt::eval_grad_f(int n, const double* x, bool new_x, double* grad) {

	for (int i = 0; i < _numVariables; i++)
		grad[i] =
				_theta[i] - log(x[i]) + log(1.0-x[i]);

	return true;
}

// return the value of the constraints Ax
bool IpOpt::eval_g(int n, const double* x, bool new_x, int m, double* values) {


	int next = 0;
	for (int j = 0; j < _numConstraints; j++) {

		std::vector<int>::iterator    var  = _constraints[j].vars.begin();
		std::vector<double>::iterator coef = _constraints[j].coefs.begin();

		double value = 0.0;
		while (var != _constraints[j].vars.end()) {

			value += (*coef)*x[*var];

			++var;
			++coef;
		}

		values[next] = value;
		++next;
	}

	return true;
}

// return the structure or values of the jacobian
bool IpOpt::eval_jac_g(
		int n, const double* x, bool new_x,
		int m, int nele_jac, int* iRow, int *jCol,
		double* values) {

	if (values == NULL) {
		// return the structure of A

		int next = 0;
		for (int j = 0; j < _numConstraints; j++)
			for (std::vector<int>::iterator var = _constraints[j].vars.begin();
			     var != _constraints[j].vars.end(); ++var) {
				iRow[next] = j;
				jCol[next] = *var;
				++next;
			}
	}
	else {

		// return the values of A
		int next = 0;
		for (int j = 0; j < _numConstraints; j++)
			for (std::vector<double>::iterator coef = _constraints[j].coefs.begin();
			     coef != _constraints[j].coefs.end(); ++coef) {
				values[next] = *coef;
				++next;
			}
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
		// Return the structure. This is a symmetric matrix, fill the lower left
		// triangle only.

		// Only diagonal entries
		for (int i = 0; i < _numVariables; i++) {
			iRow[i] = i;
			jCol[i] = i;
		}
	} else {
		// Return the values. This is a symmetric matrix, fill the lower left
		// triangle only.

		// Only diagonal entries
		for (int i = 0; i < _numVariables; i++) {
			values[i] = -1.0/x[i] - 1.0/(1.0-x[i]);
		}
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

	// store solution x in _marginals

	for (int i = 0; i < _numVariables; i++) {
		_marginals[i][0] = 1.0 - x[i];
		_marginals[i][1] = x[i];
	}
}
