/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      gap_service.c
 *
 *  DESCRIPTION
 *      This file defines routines for using GAP service.
 *
 *****************************************************************************/

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <gatt.h>
#include <gatt_prim.h>
#include <mem.h>
#include <string.h>
#include <buf_utils.h>

/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "app_gatt.h"
#include "gap_service.h"
#include "app_gatt_db.h"

/*============================================================================*
 *  Private Data Types
 *============================================================================*/

/* GAP service data type */
typedef struct
{
    /* Name length in Bytes */
    uint16  length;

    /* Pointer to hold device name used by the application */
    uint8   *p_dev_name;

} GAP_DATA_T;

/*============================================================================*
 *  Private Data
 *============================================================================*/

/* GAP service data instance */
static GAP_DATA_T g_gap_data;

/* Default device name - Added two for storing AD Type and Null ('\0') */ 
uint8 g_device_name[DEVICE_NAME_MAX_LENGTH + 2] = {
AD_TYPE_LOCAL_NAME_COMPLETE, 
'A', 'W', 'G', 'T', '\0'};


/*============================================================================*
 *  Private Definitions
 *============================================================================*/

/* Number of words of NVM memory used by GAP service */

/* Add space for Device Name Length and Device Name */
#define GAP_SERVICE_NVM_MEMORY_WORDS  (1 + DEVICE_NAME_MAX_LENGTH)

/* The offset of data being stored in NVM for GAP service. This offset is 
 * added to GAP service offset to NVM region (see g_gap_data.nvm_offset) 
 * to get the absolute offset at which this data is stored in NVM
 */
#define GAP_NVM_DEVICE_LENGTH_OFFSET  (0)

#define GAP_NVM_DEVICE_NAME_OFFSET    (1)

/*============================================================================*
 *  Public Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME 
 *      GapDataInit
 *
 *  DESCRIPTION
 *      This function is used to initialise GAP service data 
 *      structure.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void GapDataInit(void)
{
    /* Skip 1st byte to move over AD Type field and point to device name */
    g_gap_data.p_dev_name = (g_device_name + 1);
    g_gap_data.length = StrLen((char *)g_gap_data.p_dev_name);
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      GapHandleAccessRead
 *
 *  DESCRIPTION
 *      This function handles read operation on GAP service attributes
 *      maintained by the application and responds with the GATT_ACCESS_RSP 
 *      message.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void GapHandleAccessRead(GATT_ACCESS_IND_T *p_ind)
{
    uint16 length = 0;
    uint8  *p_value = NULL;
    sys_status rc = sys_status_success;

    switch(p_ind->handle)
    {

        case HANDLE_DEVICE_NAME:
        {
            /* Validate offset against length, it should be less than 
             * device name length
             */
            if(p_ind -> offset < g_gap_data.length)
            {
                length = g_gap_data.length - p_ind -> offset;
                p_value = (g_gap_data.p_dev_name + p_ind -> offset);
            }
            else
            {
                rc = gatt_status_invalid_offset;
            }
        }
        break;

        default:
            /* No more IRQ characteristics */
            rc = gatt_status_read_not_permitted;
        break;

    }

    /* Send the Gatt response. */
    GattAccessRsp(p_ind->cid, p_ind->handle, rc,
                  length, p_value);

}


/*----------------------------------------------------------------------------*
 *  NAME
 *      GapCheckHandleRange
 *
 *  DESCRIPTION
 *      This function is used to check if the handle belongs to the GAP 
 *      service
 *
 *  RETURNS
 *      Boolean - Indicating whether handle falls in range or not.
 *
 *---------------------------------------------------------------------------*/

extern bool GapCheckHandleRange(uint16 handle)
{
    return ((handle >= HANDLE_GAP_SERVICE) &&
            (handle <= HANDLE_GAP_SERVICE_END))
            ? TRUE : FALSE;
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      GapGetNameAndLength
 *
 *  DESCRIPTION
 *      This function is used to get the reference to the 'g_device_name' array, 
 *      which contains AD Type and device name. This function also returns the 
 *      AD Type and device name length.
 *
 *  RETURNS
 *      Pointer to device name array.
 *
 *---------------------------------------------------------------------------*/

extern uint8 *GapGetNameAndLength(uint16 *p_name_length)
{
    /* Update the device name length. */
    *p_name_length = StrLen((char *)g_device_name);

    /* Return the device name pointer */
    return (g_device_name);
}
