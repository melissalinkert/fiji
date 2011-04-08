set(COINHSL_NAMES ${COINHSL_NAMES} coinhsl libcoinhsl)
find_library(COINHSL_LIBRARY NAMES ${COINHSL_NAMES} )

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(COINHSL DEFAULT_MSG COINHSL_LIBRARY)

mark_as_advanced(COINHSL_INCLUDE_DIR COINHSL_LIBRARY)
