set(COINMETIS_NAMES ${COINMETIS_NAMES} coinmetis libcoinmetis)
find_library(COINMETIS_LIBRARY NAMES ${COINMETIS_NAMES} )

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(COINMETIS DEFAULT_MSG COINMETIS_LIBRARY)

mark_as_advanced(COINMETIS_INCLUDE_DIR COINMETIS_LIBRARY)
