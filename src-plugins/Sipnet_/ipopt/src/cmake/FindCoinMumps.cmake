set(COINMUMPS_NAMES ${COINMUMPS_NAMES} coinmumps libcoinmumps)
find_library(COINMUMPS_LIBRARY NAMES ${COINMUMPS_NAMES} )

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(COINMUMPS DEFAULT_MSG  COINMUMPS_LIBRARY)

mark_as_advanced(COINMUMPS_INCLUDE_DIR COINMUMPS_LIBRARY )
