#include <vector>

#include "IpIpoptApplication.hpp"
#include "IpTNLP.hpp"

using namespace Ipopt;

class IpOpt : public TNLP {

	struct Constraint {

		std::vector<int>    vars;
		std::vector<double> coefs;
		int                 relation;
		double              value;
	};

public:

	IpOpt(int numNodes, int numConstraints);

	void setSingleSiteFactor(int node, double value0, double value1);

	void setEdgeFactor(int node1, int node2,
	                   double value00, double value01,
	                   double value10, double value11);

	void setFactor(int numNodes, int* nodes, double* values);

	void setLinearConstraint(int numNodes, int* nodes, double* coefficients,
	                         int relation, double value);

	void inferMarginals(int numThreads);

	int getState(int node);

	double getMarginal(int node, int state);

	/////////////////////////////
	// IpOpt interface methods //
	/////////////////////////////

	/** Method to return some info about the nlp */
	virtual bool get_nlp_info(
			int& n, int& m, int& nnz_jac_g,
			int& nnz_h_lag, IndexStyleEnum& index_style);

	/** Method to return the bounds for my problem */
	virtual bool get_bounds_info(
			int n, double* x_l, double* x_u,
			int m, double* g_l, double* g_u);

	/** Method to return the starting point for the algorithm */
	virtual bool get_starting_point(
			int n, bool init_x, double* x,
			bool init_z, double* z_L, double* z_U,
			int m, bool init_lambda,
			double* lambda);

	/** Method to return the objective value */
	virtual bool eval_f(int n, const double* x, bool new_x, double& obj_value);

	/** Method to return the gradient of the objective */
	virtual bool eval_grad_f(int n, const double* x, bool new_x, double* grad_f);

	/** Method to return the constraint residuals */
	virtual bool eval_g(int n, const double* x, bool new_x, int m, double* g);

	/** Method to return:
	 *	 1) The structure of the jacobian (if "values" is NULL)
	 *	 2) The values of the jacobian (if "values" is not NULL)
	 */
	virtual bool eval_jac_g(
			int n, const double* x, bool new_x,
			int m, int nele_jac, int* iRow, int *jCol,
			double* values);

	/** Method to return:
	 *	 1) The structure of the hessian of the lagrangian (if "values" is NULL)
	 *	 2) The values of the hessian of the lagrangian (if "values" is not NULL)
	 */
	virtual bool eval_h(
			int n, const double* x, bool new_x,
			double obj_factor, int m, const double* lambda,
			bool new_lambda, int nele_hess, int* iRow,
			int* jCol, double* values);

	/** This method is called when the algorithm is complete so the TNLP can store/write the solution */
	virtual void finalize_solution(
			SolverReturn status,
			int n, const double* x, const double* z_L, const double* z_U,
			int m, const double* g, const double* lambda,
			double obj_value,
			const IpoptData* ip_data,
			IpoptCalculatedQuantities* ip_cq);
private:

	IpOpt(const IpOpt& other);
	IpOpt& operator=(const IpOpt& other);

	std::vector<double>               _theta;
	std::vector<std::vector<double> > _marginals;
	std::vector<Constraint>           _constraints;

	int _numVariables;
	int _numConstraints;
	int _nextConstraint;

	int      _numEntriesA;

	double _constTerm;
};
