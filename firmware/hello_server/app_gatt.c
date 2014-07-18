/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      app_gatt.c
 *
 *  DESCRIPTION
 *      GATT-related routines implementations
 *
 *****************************************************************************/

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <ls_app_if.h>
#include <gap_app_if.h>
#include <gap_types.h>
#include <ls_err.h>
#include <ls_types.h>
#include <panic.h>
#include <gatt.h>
#include <gatt_uuid.h>

/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "app_main.h"
#include "app_gatt_db.h"
#include "app_gatt.h"
#include "appearance.h"
#include "gap_service.h"
#include "hello_service_uuids.h"
#include "hello_service.h"

/*============================================================================*
 *  Private Definitions
 *============================================================================*/

/* This constant is used in the main server app to define array that is 
   large enough to hold the advertisement data.
 */
#define MAX_ADV_DATA_LEN                                  (31)

/* Acceptable shortened device name length that can be sent in advertisement 
 * data 
 */
#define SHORTENED_DEV_NAME_LEN                            (8)

/* Length of Tx Power prefixed with 'Tx Power' AD Type */
#define TX_POWER_VALUE_LENGTH                             (2)

/*============================================================================*
 *  Private Function Prototypes
 *============================================================================*/
/* This function is used to add device name to advertisement or scan response */
static void addDeviceNameToAdvData(uint16 adv_data_len, uint16 scan_data_len);

/* This function is used to set advertisement parameters */
static void gattSetAdvertParams(void);

/*============================================================================*
 *  Private Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      addDeviceNameToAdvData
 *
 *  DESCRIPTION
 *      This function is used to add device name to advertisement or scan 
 *      response data. It follows below steps:
 *      a. Try to add complete device name to the advertisment packet
 *      b. Try to add complete device name to the scan response packet
 *      c. Try to add shortened device name to the advertisement packet
 *      d. Try to add shortened (max possible) device name to the scan 
 *         response packet
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void addDeviceNameToAdvData(uint16 adv_data_len, uint16 scan_data_len)
{
    uint8 *p_device_name = NULL;
    uint16 device_name_adtype_len;

    /* Read device name along with AD Type and its length */
    p_device_name = GapGetNameAndLength(&device_name_adtype_len);

    /* Add complete device name to Advertisement data */
    p_device_name[0] = AD_TYPE_LOCAL_NAME_COMPLETE;

    /* Increment device_name_length by one to account for length field
     * which will be added by the GAP layer. 
     */

    /* Check if Complete Device Name can fit in remaining advertisement 
     * data space 
     */
    if((device_name_adtype_len + 1) <= (MAX_ADV_DATA_LEN - adv_data_len))
    {
        /* Add Complete Device Name to Advertisement Data */
        if (LsStoreAdvScanData(device_name_adtype_len , p_device_name, 
                      ad_src_advertise) != ls_err_none)
        {
            ReportPanic(app_panic_set_advert_data);
        }

    }
    /* Check if Complete Device Name can fit in Scan response message */
    else if((device_name_adtype_len + 1) <= (MAX_ADV_DATA_LEN - scan_data_len)) 
    {
        /* Add Complete Device Name to Scan Response Data */
        if (LsStoreAdvScanData(device_name_adtype_len , p_device_name, 
                      ad_src_scan_rsp) != ls_err_none)
        {
            ReportPanic(app_panic_set_scan_rsp_data);
        }

    }
    /* Check if Shortened Device Name can fit in remaining advertisement 
     * data space 
     */
    else if((MAX_ADV_DATA_LEN - adv_data_len) >=
            (SHORTENED_DEV_NAME_LEN + 2)) /* Added 2 for Length and AD type 
                                           * added by GAP layer
                                           */
    {
        /* Add shortened device name to Advertisement data */
        p_device_name[0] = AD_TYPE_LOCAL_NAME_SHORT;

       if (LsStoreAdvScanData(SHORTENED_DEV_NAME_LEN , p_device_name, 
                      ad_src_advertise) != ls_err_none)
        {
            ReportPanic(app_panic_set_advert_data);
        }

    }
    else /* Add device name to remaining Scan reponse data space */
    {
        /* Add as much as can be stored in Scan Response data */
        p_device_name[0] = AD_TYPE_LOCAL_NAME_SHORT;

       if (LsStoreAdvScanData(MAX_ADV_DATA_LEN - scan_data_len, 
                                    p_device_name, 
                                    ad_src_scan_rsp) != ls_err_none)
        {
            ReportPanic(app_panic_set_scan_rsp_data);
        }

    }

}


/*----------------------------------------------------------------------------*
 *  NAME
 *      gattSetAdvertParams
 *
 *  DESCRIPTION
 *      This function is used to set advertisement parameters 
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void gattSetAdvertParams(void)
{
    uint8 advert_data[MAX_ADV_DATA_LEN];
    uint16 length;

    uint32 adv_interval_min = RP_ADVERTISING_INTERVAL_MIN;
    uint32 adv_interval_max = RP_ADVERTISING_INTERVAL_MAX;
    /*Tx Power*/
    int8 tx_power_level = 0xff; /* Signed value */

    /* Tx power level value prefixed with 'Tx Power' AD Type */
    /* Refer to BT4.0 specification, Vol3-part-C-Section-11.1.5*/ 
    uint8 device_tx_power[TX_POWER_VALUE_LENGTH] = {
                AD_TYPE_TX_POWER
                };

    uint8 device_appearance[ATTR_LEN_DEVICE_APPEARANCE + 1] = {
                AD_TYPE_APPEARANCE,
                WORD_LSB(APPEARANCE_APPLICATION_VALUE),
                WORD_MSB(APPEARANCE_APPLICATION_VALUE)
                };

    /* A variable to keep track of the data added to AdvData. The limit is 
     * MAX_ADV_DATA_LEN. GAP layer will add AD Flags to AdvData which 
     * is 3 bytes. Refer BT Spec 4.0, Vol 3, Part C, Sec 11.1.3.
     */
    /* first byte is length
     * second byte is AD TYPE = 0x1
     * Third byte is Flags description 
     */
    uint16 length_added_to_adv = 3;

    if((GapSetMode(gap_role_peripheral, gap_mode_discover_general,
                        gap_mode_connect_undirected, 
                        gap_mode_bond_yes,
                        gap_mode_security_unauthenticate) != ls_err_none) ||
       (GapSetAdvInterval(adv_interval_min, adv_interval_max) 
                        != ls_err_none))
    {
        ReportPanic(app_panic_set_advert_params);
    }


    /* Reset existing advertising data */
    if(LsStoreAdvScanData(0, NULL, ad_src_advertise) != ls_err_none)
    {
        ReportPanic(app_panic_set_advert_data);
    }

    /* Reset existing scan response data */
    if(LsStoreAdvScanData(0, NULL, ad_src_scan_rsp) != ls_err_none)
    {
        ReportPanic(app_panic_set_scan_rsp_data);
    }

    /* Setup ADVERTISEMENT DATA */

    /* Add UUID list of the services supported by the device */
    length = GetSupported128BitUUIDServiceList(advert_data);

    /* One added for Length field, which will be added to Adv Data by GAP 
     * layer 
     */
    length_added_to_adv += (length + 1);

    if (LsStoreAdvScanData(length, advert_data, 
                        ad_src_advertise) != ls_err_none)
    {
        ReportPanic(app_panic_set_advert_data);
    }

    /* One added for Length field, which will be added to Adv Data by GAP 
     * layer 
     */
    length_added_to_adv += (sizeof(device_appearance) + 1);

    /* Add device appearance to the advertisements */
    if (LsStoreAdvScanData(ATTR_LEN_DEVICE_APPEARANCE + 1, 
        device_appearance, ad_src_advertise) != ls_err_none)
    {
        ReportPanic(app_panic_set_advert_data);
    }

    /* Read tx power of the chip */
    if(LsReadTransmitPowerLevel(&tx_power_level) != ls_err_none)
    {
        /* Reading tx power failed */
        ReportPanic(app_panic_read_tx_pwr_level);
    }

    /* Add the read tx power level to device_tx_power 
     * Tx power level value is of 1 byte 
     */
    device_tx_power[TX_POWER_VALUE_LENGTH - 1] = (uint8 )tx_power_level;

    /* One added for Length field, which will be added to Adv Data by GAP 
     * layer 
     */
    length_added_to_adv += (TX_POWER_VALUE_LENGTH + 1);

    /* Add tx power value of device to the advertising data */
    if (LsStoreAdvScanData(TX_POWER_VALUE_LENGTH, device_tx_power, 
                          ad_src_advertise) != ls_err_none)
    {
        ReportPanic(app_panic_set_advert_data);
    }

    addDeviceNameToAdvData(length_added_to_adv, 0);

}


/*============================================================================*
 *  Public Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      HandleAccessRead
 *
 *  DESCRIPTION
 *      This function handles read operation on attributes (as received in 
 *      GATT_ACCESS_IND message) maintained by the application and respond 
 *      with the GATT_ACCESS_RSP message.
 *
 *  RETURNS
 *      Nothing
 *
 *---------------------------------------------------------------------------*/

extern void HandleAccessRead(GATT_ACCESS_IND_T *p_ind)
{
    /* For the received attribute handle, check all the services that support 
     * attribute 'Read' operation handled by application.
     */
    /* User can add more services here to support the 
     * services read operation 
     */
    if(GapCheckHandleRange(p_ind->handle))
    {
        /* Attribute handle belongs to GAP service */
        GapHandleAccessRead(p_ind);
    }
    else if(HelloServiceCheckHandleRange(p_ind->handle))
    {
        /* Attribute handle belongs to Hello service */
        HelloServiceHandleAccessRead(p_ind);
    }
    else
    {
        /* Application doesn't support 'Read' operation on received 
         * attribute handle, hence return 'gatt_status_read_not_permitted'
         * status
         */
        GattAccessRsp(p_ind->cid, p_ind->handle, 
                      gatt_status_read_not_permitted,
                      0, NULL);
    }

}


/*----------------------------------------------------------------------------*
 *  NAME
 *      HandleAccessWrite
 *
 *  DESCRIPTION
 *      This function handles Write operation on attributes (as received in 
 *      GATT_ACCESS_IND message) maintained by the application.
 *
 *  RETURNS
 *      Nothing
 *
 *---------------------------------------------------------------------------*/

extern void HandleAccessWrite(GATT_ACCESS_IND_T *p_ind)
{
    /* For the received attribute handle, check all the services that support 
     * attribute 'Write' operation handled by application.
     */
    /* User can add more services here to support 
     * the services write operation
     */

    /* Application doesn't support 'Write' operation on received 
     * attribute handle, hence return 'gatt_status_write_not_permitted'
     * status
     */
    GattAccessRsp(p_ind->cid, p_ind->handle, 
                  gatt_status_write_not_permitted,
                  0, NULL);


}


/*----------------------------------------------------------------------------*
 *  NAME
 *      GattStartAdverts
 *
 *  DESCRIPTION
 *      This function is used to start undirected advertisements and moves to 
 *      ADVERTISING state.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void GattStartAdverts(void)
{
    /* Variable 'connect_flags' needs to be updated to have peer address type 
     * if Directed advertisements are supported as peer address type will 
     * only be used in that case. We don't support directed advertisements for 
     * this application.
     */

    uint16 connect_flags = L2CAP_CONNECTION_SLAVE_UNDIRECTED | 
                      L2CAP_OWN_ADDR_TYPE_PUBLIC;

    /* Set advertisement parameters */
    gattSetAdvertParams();

    /* Start GATT connection in Slave role */
    GattConnectReq(NULL, connect_flags);

    AppStartAdvertTimer(CONNECTION_ADVERT_TIMEOUT_VALUE);
}

/*----------------------------------------------------------------------------*
 *  NAME
 *      GetSupported128BitUUIDServiceList
 *
 *  DESCRIPTION
 *      This function prepares the list of supported 128-bit service UUIDs to
 *      be added to Advertisement data. It also adds the relevant AD Type to
 *      the starting of AD array.
 *
 *  RETURNS
 *      Return the size AD Service UUID data.
 *
 *---------------------------------------------------------------------------*/

extern uint16 GetSupported128BitUUIDServiceList(uint8 *p_service_uuid_ad)
{
    uint8   size_data = 0;

    /* Add 128-bit UUID for supported main service */
    p_service_uuid_ad[size_data++] = AD_TYPE_SERVICE_UUID_128BIT_LIST;

    /* Add hello service UUID*/
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_16;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_15;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_14;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_13;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_12;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_11;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_10;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_9;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_8;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_7;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_6;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_5;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_4;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_3;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_2;
    p_service_uuid_ad[size_data++] = UUID_HELLO_SERVICE_1;

    /* Add all the supported UUID in this function*/

    /* Return the size of AD service data. */
    return ((uint16)size_data);

}


/*----------------------------------------------------------------------------*
 *  NAME
 *      GattStopAdverts
 *
 *  DESCRIPTION
 *      This function is used to stop Advertisement.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern  void GattStopAdverts(void)
{
    switch(AppGetState())
    {
        case app_state_advertising:
        {
            /* Stop on-going advertisements */
            GattCancelConnectReq();
        }
        break;

        default:
        {
            /* Ignore timer in remaining states */
        }
        break;
    }
}
