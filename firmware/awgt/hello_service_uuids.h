/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      hello_service_uuids.h
 *
 *  DESCRIPTION
 *      UUID MACROs for custom profile service (Hello Service)
 *
 *****************************************************************************/

#ifndef __HELLO_SERVICE_UUIDS_H__
#define __HELLO_SERVICE_UUIDS_H__

/*============================================================================*
 *  Public Definitions
 *============================================================================*/

/* Brackets should not be used around the value of a macro. The parser 
 * which creates .c and .h files from .db file doesn't understand  brackets 
 * and will raise syntax errors.
 */

/* For UUID values, refer http://developer.bluetooth.org/
 */

#define UUID_HELLO_SERVICE               0x5ab20001b3554d8a96ef2963812dd0b8

#define UUID_USERNAME                    0x5ab20002b3554d8a96ef2963812dd0b8

/*Split the 128bit UUID into 8-bit*/
#define UUID_HELLO_SERVICE_1      0x5a
#define UUID_HELLO_SERVICE_2      0xb2
#define UUID_HELLO_SERVICE_3      0x00
#define UUID_HELLO_SERVICE_4      0x01
#define UUID_HELLO_SERVICE_5      0xb3
#define UUID_HELLO_SERVICE_6      0x55
#define UUID_HELLO_SERVICE_7      0x4d
#define UUID_HELLO_SERVICE_8      0x8a
#define UUID_HELLO_SERVICE_9      0x96
#define UUID_HELLO_SERVICE_10     0xef
#define UUID_HELLO_SERVICE_11     0x29
#define UUID_HELLO_SERVICE_12     0x63
#define UUID_HELLO_SERVICE_13     0x81
#define UUID_HELLO_SERVICE_14     0x2d
#define UUID_HELLO_SERVICE_15     0xd0
#define UUID_HELLO_SERVICE_16     0xb8

#endif /* __HELLO_SERVICE_UUIDS_H__ */