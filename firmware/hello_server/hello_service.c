/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 * FILE
 *     hello_service.c
 *
 * DESCRIPTION
 *     This file defines routines for using Hello service.
 *
 ****************************************************************************/

/*============================================================================*
 *  SDK Header Files
 *===========================================================================*/

#include <gatt.h>
#include <gatt_prim.h>
#include <buf_utils.h>

/*============================================================================*
 *  Local Header Files
 *===========================================================================*/

#include "app_main.h"
#include "hello_service.h"
#include "app_gatt_db.h"

/*============================================================================*
 *  Private Macro Definitions
 *===========================================================================*/
#define USERNAME_MAX_LENGTH             20

/*============================================================================*
 *  Private Data Types
 *===========================================================================*/

/* Hello service data type */
typedef struct
{
    /* pointer to username value */
    uint8 *p_username;
} HELLO_SERVICE_DATA_T;

/* username('\0') */ 
static uint8 g_username[] = { 
'C', 'a', 'm', 'b', 'r', 'i', 'd', 'g', 'e', '\0'};
/*============================================================================*
 *  Private Data
 *===========================================================================*/

/* Hello service data instance */
static HELLO_SERVICE_DATA_T g_hs_data;


/*============================================================================*
 *  Public Function Implementations
 *===========================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      HelloServiceDataInit
 *
 *  DESCRIPTION
 *      This function is used to initialise Hello Service data 
 *      structure.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void HelloServiceDataInit(void)
{
    g_hs_data.p_username = g_username;
}

/*----------------------------------------------------------------------------*
 *  NAME
 *      HelloServiceInitChipReset
 *
 *  DESCRIPTION
 *      This function is used to initialise Hello service data 
 *      structure at chip reset
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void HelloServiceInitChipReset(void)
{
    /* Nothing to do*/
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      HelloServiceHandleAccessRead
 *
 *  DESCRIPTION
 *      This function handles read operation on hello service attributes
 *      maintained by the application and responds with the GATT_ACCESS_RSP
 *      message.
 *
 *  RETURNS
 *      Nothing. 
 *
 *---------------------------------------------------------------------------*/

extern void HelloServiceHandleAccessRead(GATT_ACCESS_IND_T *p_ind)
{
    uint16 length = 0;
    uint8* data;
    sys_status rc = sys_status_success;
    
    switch(p_ind->handle)
    {
        case HANDLE_USERNAME:
            length = (sizeof(g_username) - 1) > USERNAME_MAX_LENGTH ? 
                     USERNAME_MAX_LENGTH : 
                     sizeof(g_username) - 1;
            data = g_hs_data.p_username;
        break;
        
        default:
            /* No more IRQ characteristics */
            rc = gatt_status_read_not_permitted;
        break;
    }

    /* Send Access response */
    GattAccessRsp(p_ind->cid, p_ind->handle, rc, length, data);
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      HelloServiceCheckHandleRange
 *
 *  DESCRIPTION
 *      This function is used to check if the handle belongs to the hello 
 *      service
 *
 *  RETURNS
 *      Boolean - Indicating whether handle falls in range or not.
 *
 *---------------------------------------------------------------------------*/

extern bool HelloServiceCheckHandleRange(uint16 handle)
{
    return ((handle >= HANDLE_HELLO_SERVICE) &&
            (handle <= HANDLE_HELLO_SERVICE_END))
            ? TRUE : FALSE;
}
