find_path(IPOPT_INCLUDE_DIR IpoptConfig.h PATH_SUFFIXES coin)

# ipopt
set(IPOPT_NAMES ${IPOPT_NAMES} ipopt libipopt)
find_library(IPOPT_LIBRARY NAMES ${IPOPT_NAMES} )

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(IPOPT  DEFAULT_MSG  IPOPT_LIBRARY IPOPT_INCLUDE_DIR)

mark_as_advanced(IPOPT_INCLUDE_DIR IPOPT_LIBRARY )
