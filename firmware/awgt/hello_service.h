/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      hello_service.h
 *
 *  DESCRIPTION
 *      Header definitions for hello service
 *
 *****************************************************************************/

#ifndef __HELLO_SERVICE_H__
#define __HELLO_SERVICE_H__

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <types.h>
#include <bt_event_types.h>

/*============================================================================*
 *  Public Function Prototypes
 *============================================================================*/

/* This function is used to initialise Hello service data structure.*/
extern void HelloServiceDataInit(void);

/* This function is used to initialise Hello service data structure at 
 * chip reset
 */
extern void HelloServiceInitChipReset(void);

/* This function handles write operation on Hello service attributes
 * maintained by the application
 */
extern void HelloServiceHandleAccessWrite(GATT_ACCESS_IND_T *p_ind);

/* This function handles read operation on Hello service attributes
 * maintained by the application
 */
extern void HelloServiceHandleAccessRead(GATT_ACCESS_IND_T *p_ind);

/* This function is used to check if the handle belongs to the Hello 
 * service
 */
extern bool HelloServiceCheckHandleRange(uint16 handle);

#endif /* __HELLO_SERVICE_H__ */
