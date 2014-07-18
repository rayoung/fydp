/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      app_gatt.h
 *
 *  DESCRIPTION
 *      Header definitions for common application attributes
 *
******************************************************************************/

#ifndef __APP_GATT_H__
#define __APP_GATT_H__

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <types.h>
#include <time.h>
#include <gatt_prim.h>

/*============================================================================*
 *  Local Header Files
 *============================================================================*/
#include "user_config.h"

/*============================================================================*
 *  Public Definitions
 *============================================================================*/

/* Invalid UCID indicating we are not currently connected */
#define GATT_INVALID_UCID                    (0xFFFF)

/* Invalid Attribute Handle */
#define INVALID_ATT_HANDLE                   (0x0000)

/* AD Type for Appearance */
#define AD_TYPE_APPEARANCE                   (0x19)

/* Maximum Length of Device Name 
 * Note: Do not increase device name length beyond (DEFAULT_ATT_MTU -3 = 20) 
 * octets as GAP service at the moment doesn't support handling of Prepare 
 * write and Execute write procedures.
 */
#define DEVICE_NAME_MAX_LENGTH               (20)

/*============================================================================*
 *  Public Data Types
 *============================================================================*/

/* GATT Client Characteristic Configuration Value [Ref GATT spec, 3.3.3.3]*/
typedef enum
{
    gatt_client_config_none            = 0x0000,
    gatt_client_config_notification    = 0x0001,
    gatt_client_config_indication      = 0x0002,
    gatt_client_config_reserved        = 0xFFF4

} gatt_client_config;

/*  Application defined panic codes */
typedef enum
{
    /* Failure while setting advertisement parameters */
    app_panic_set_advert_params = 0x1,

    /* Failure while setting advertisement data */
    app_panic_set_advert_data,

    /* Failure while setting scan response data */
    app_panic_set_scan_rsp_data,

    /* Failure while registering GATT DB with firmware */
    app_panic_db_registration,

    /* Failure while reading NVM */
    app_panic_nvm_read,

    /* Failure while writing NVM */
    app_panic_nvm_write,

    /* Failure while reading Tx Power Level */
    app_panic_read_tx_pwr_level,

    /* Failure while triggering connection parameter update procedure */
    app_panic_con_param_update,

    /* Event received in an unexpected application state */
    app_panic_invalid_state,

    /* Unexpected beep type */
    app_panic_unexpected_beep_type

}app_panic_code;

typedef enum 
{
    /* Application Initial State */
    app_state_init = 0,

    /* Enters when undirected advertisements are configured */
    app_state_advertising,

    /* Enters when connection is established with the host */
    app_state_connected,

    /* Enters when disconnect is initiated by the application */
    app_state_disconnecting,

    /* Enters when the application is neither advertising nor connected to any 
     * remote host 
     */
    app_state_idle

} app_state;

/*============================================================================*
 *  Public Function Prototypes
 *============================================================================*/
/* This function handles read operation on attributes (as received in 
 * GATT_ACCESS_IND message) maintained by the application
 */
extern void HandleAccessRead(GATT_ACCESS_IND_T *p_ind);

/* This function handles Write operation on attributes (as received in 
 * GATT_ACCESS_IND message) maintained by the application.
 */
extern void HandleAccessWrite(GATT_ACCESS_IND_T *p_ind);

/* This function is used to start undirected advertisements and moves to 
 * ADVERTISING state
 */
extern void GattStartAdverts(void);

/* This function is used to stop on-going advertisements */
extern void GattStopAdverts(void);

/* This function prepares the list of supported 128-bit service UUIDs to be 
 * added to Advertisement data
 */
extern uint16 GetSupported128BitUUIDServiceList(uint8 *p_service_uuid_ad);

#endif /* __APP_GATT_H__ */
