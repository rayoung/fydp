/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      gap_conn_params.h
 *
 *  DESCRIPTION
 *      MACROs for connection and advertisement parameter values
 *
 *****************************************************************************/

#ifndef __GAP_CONN_PARAMS_H__
#define __GAP_CONN_PARAMS_H__

/*============================================================================*
 *  Public Definitions
 *============================================================================*/

/* Advertising parameters, time is expressed in microseconds and the firmware
 * will round this down to the nearest slot. Acceptable range is 20ms to 10.24s
 * and the minimum must be no larger than the maximum.This value needs to be 
 * modified at later stage as decided GPA for specific profile.
 *
 * For Application, use 1s to 2.5s as the connection advertisement interval 
 * for reduced power connections. Vendors will need to tune these 
 * values as per their requirements.
 */

#define RP_ADVERTISING_INTERVAL_MIN         (1280 * MILLISECOND)
#define RP_ADVERTISING_INTERVAL_MAX         (1280 * MILLISECOND)

/* Maximum number of connection parameter update requests that can be send when 
 * connected
 */
#define MAX_NUM_CONN_PARAM_UPDATE_REQS      (2)

/* Brackets should not be used around the value of a macros used in db files. 
 * The parser which creates .c and .h files from .db file doesn't understand 
 * brackets and will raise syntax errors.
 */

/* Preferred connection parameter values should be within the range specified by
 * the bluetooth specification.
 */

/* Minimum and maximum connection interval in number of frames */
#define PREFERRED_MAX_CON_INTERVAL          0x0020 /* 40 ms */
#define PREFERRED_MIN_CON_INTERVAL          0x0010 /* 20 ms */

/* Slave latency in number of connection intervals */
#define PREFERRED_SLAVE_LATENCY             0x0001 /* 1 conn_intervals */

/* Supervision timeout (ms) = PREFERRED_SUPERVISION_TIMEOUT * 10 ms */
#define PREFERRED_SUPERVISION_TIMEOUT       0x00C8 /* 2 seconds */

#endif /* __GAP_CONN_PARAMS_H__ */
