/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      app_main.c
 *
 *  DESCRIPTION
 *      This file defines a simple implementation of a hello server.
 *
 ******************************************************************************/

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <main.h>
#include <types.h>
#include <timer.h>
#include <mem.h>
#include <config_store.h>
#include <debug.h>


/* Upper Stack API */
#include <gatt.h>
#include <gatt_prim.h>
#include <ls_app_if.h>
#include <gap_app_if.h>
#include <buf_utils.h>
#include <security.h>
#include <panic.h>
#include <nvm.h>
#include <random.h>


/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "app_gatt.h"
#include "app_gatt_db.h"
#include "buzzer.h"
#include "app_main.h"
#include "app_hw.h"
#include "gap_service.h"
#include "hello_service.h"

/*============================================================================*
 *  Private Definitions
 *============================================================================*/

/* Maximum number of timers */
#define MAX_APP_TIMERS                 (3)

/* Slave device is not allowed to transmit another Connection Parameter 
 * Update request till time TGAP(conn_param_timeout). Refer to section 9.3.9.2,
 * Vol 3, Part C of the Core 4.0 BT spec. The application should retry the 
 * 'Connection Parameter Update' procedure after time TGAP(conn_param_timeout)
 * which is 30 seconds.
 */
#define GAP_CONN_PARAM_TIMEOUT          (30 * SECOND)

/*============================================================================*
 *  Private Data types
 *============================================================================*/

/* Application data structure */
typedef struct
{
    /* Current state of application */
    app_state                  state;

    /* TYPED_BD_ADDR_T of the host to which device is connected */
    TYPED_BD_ADDR_T            con_bd_addr;

    /* Track the UCID as Clients connect and disconnect */
    uint16                     st_ucid;

    /* Store timer id for Connection Parameter Update timer in Connected 
     * state
     */
    timer_id                   con_param_update_tid;

    /* Variable to keep track of number of connection parameter update 
     * requests made 
     */
    uint8                      num_conn_update_req;

    /* Store timer id while doing 'UNDIRECTED ADVERTS' and activity on the 
     * sensor device like measurements or user intervention in CONNECTED' state.
     */
    timer_id                   app_tid;

    /* Varibale to store the current connection interval being used. */
    uint16                     conn_interval;

    /* Variable to store the current slave latency. */
    uint16                     conn_latency;

    /*Variable to store the current connection timeout value. */
    uint16                     conn_timeout;
} APP_DATA_T;

/*============================================================================*
 *  Private Data
 *============================================================================*/

/* Declare space for application timers. */
static uint16 app_timers[SIZEOF_APP_TIMER * MAX_APP_TIMERS];

/* Application data instance. Ensure this data is not directly accessed 
 * by other components directly
 */
APP_DATA_T g_app_data;

/*============================================================================*
 *  Private Function Prototypes
 *============================================================================*/
 /* This function is called to initialise application data structure */
static void appDataInit(void);

/* This function starts the Connection update timer */
static void appStartConnUpdateTimer(void);

/* This function is used to send L2CAP_CONNECTION_PARAMETER_UPDATE_REQUEST 
 * to the remote device
 */
static void requestConnParamUpdate(timer_id tid);

/* This function is called while exiting app_state_advertising state */
static void appExitAdvertising(void);

/* This function is used to handle Advertisement timer.expiry */
static void appAdvertTimerHandler(timer_id tid);


/* AppProcessLmEvent API EVENT HANDLERS */

/* This function handles the signal GATT_ADD_DB_CFM */
static void handleSignalGattAddDbCfm(GATT_ADD_DB_CFM_T *p_event_data);

/* This function handles the signal LM_EV_CONNECTION_COMPLETE */
static void handleSignalLmEvConnectionComplete(
                             LM_EV_CONNECTION_COMPLETE_T *p_event_data);

/* This function handles the signal GATT_CANCEL_CONNECT_CFM */
static void handleSignalGattCancelConnectCfm(void);

/* This function handles the signal GATT_CONNECT_CFM */
static void handleSignalGattConnectCfm(GATT_CONNECT_CFM_T* p_event_data);

/* This function handles the signal LS_CONNECTION_PARAM_UPDATE_CFM */
static void handleSignalLsConnParamUpdateCfm(
                             LS_CONNECTION_PARAM_UPDATE_CFM_T *p_event_data);

/* This function handles the signal LS_CONNECTION_PARAM_UPDATE_IND */
static void handleSignalLsConnParamUpdateInd(
                             LS_CONNECTION_PARAM_UPDATE_IND_T *p_event_data);

/* This function handles GATT_ACCESS_IND message for attributes 
 * maintained by the application.
 */
static void handleSignalGattAccessInd(GATT_ACCESS_IND_T *p_event_data);

/* This function handles LM Disconnect Complete event which is received
 * at the completion of disconnect procedure triggered either by the 
 * device or remote host or because of link loss 
 */
static void handleSignalLmDisconnectComplete(
                    HCI_EV_DATA_DISCONNECT_COMPLETE_T *p_event_data);

static uint16 UartDataRxCallback ( void* p_data, uint16 data_count,
        uint16* p_num_additional_words );

/*============================================================================*
 *  Private Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      appDataInit
 *
 *  DESCRIPTION
 *      This function is called to initialise application data
 *      structure.
 *
 *  RETURNS/MODIFIES
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void appDataInit(void)
{
    TimerDelete(g_app_data.app_tid);
    g_app_data.app_tid = TIMER_INVALID;

    TimerDelete(g_app_data.con_param_update_tid);
    g_app_data.con_param_update_tid = TIMER_INVALID;

    g_app_data.st_ucid = GATT_INVALID_UCID;

    /* Reset the connection parameter variables. */
    g_app_data.conn_interval = 0;
    g_app_data.conn_latency = 0;
    g_app_data.conn_timeout = 0;

    /* Initialise GAP Data structure */
    GapDataInit();

    /* Hello Service data initialisation */
    HelloServiceDataInit();

    /*call the required service data initialization APIs from here*/
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      appStartConnUpdateTimer
 *
 *  DESCRIPTION
 *      This function starts the Connection update timer
 *
 *  RETURNS/MODIFIES
 *      None
 *
 *---------------------------------------------------------------------------*/

static void appStartConnUpdateTimer(void)
{
    if((g_app_data.conn_interval < PREFERRED_MIN_CON_INTERVAL ||
        g_app_data.conn_interval > PREFERRED_MAX_CON_INTERVAL
#if PREFERRED_SLAVE_LATENCY
        || g_app_data.conn_latency < PREFERRED_SLAVE_LATENCY
#endif
        )
      )
    {
        /* Set the num of conn update attempts to zero */
        g_app_data.num_conn_update_req = 0;

        /* Start timer to trigger Connection Paramter Update 
         * procedure 
         */
        g_app_data.con_param_update_tid = TimerCreate(
                            GAP_CONN_PARAM_TIMEOUT,
                            TRUE, requestConnParamUpdate);
    }
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      requestConnParamUpdate
 *
 *  DESCRIPTION
 *      This function is used to send L2CAP_CONNECTION_PARAMETER_UPDATE_REQUEST 
 *      to the remote device.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void requestConnParamUpdate(timer_id tid)
{
    /* Application specific preferred paramters */
     ble_con_params app_pref_conn_param = 
                {
                    PREFERRED_MIN_CON_INTERVAL,
                    PREFERRED_MAX_CON_INTERVAL,
                    PREFERRED_SLAVE_LATENCY,
                    PREFERRED_SUPERVISION_TIMEOUT
                };

    if(g_app_data.con_param_update_tid == tid)
    {

        g_app_data.con_param_update_tid = TIMER_INVALID;

        /*Handling signal as per current state */
        switch(g_app_data.state)
        {

            case app_state_connected:
            {
                /* Send Connection Parameter Update request using application 
                 * specific preferred connection parameters
                 */

                if(LsConnectionParamUpdateReq(&g_app_data.con_bd_addr, 
                                &app_pref_conn_param) != ls_err_none)
                {
                    ReportPanic(app_panic_con_param_update);
                }

                /* Increment the count for connection parameter update 
                 * requests 
                 */
                ++ g_app_data.num_conn_update_req;

            }
            break;

            default:
            {
                /* Ignore in other states */
            }
            break;
        }

    } /* Else ignore the timer */

}


/*----------------------------------------------------------------------------*
 *  NAME
 *      appExitAdvertising
 *
 *  DESCRIPTION
 *      This function is called while exiting app_state_advertising state.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void appExitAdvertising(void)
{
    /* Cancel advertisement timer */
    TimerDelete(g_app_data.app_tid);
    g_app_data.app_tid = TIMER_INVALID;
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      appAdvertTimerHandler
 *
 *  DESCRIPTION
 *      This function is used to handle Advertisement timer.expiry.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void appAdvertTimerHandler(timer_id tid)
{
    /* Based upon the timer id, stop on-going advertisements */
    if(g_app_data.app_tid == tid)
    {
        g_app_data.app_tid = TIMER_INVALID;

        GattStopAdverts();
    }/* Else ignore timer expiry, could be because of 
      * some race condition */
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      handleSignalGattAddDBCfm
 *
 *  DESCRIPTION
 *      This function handles the signal GATT_ADD_DB_CFM
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void handleSignalGattAddDbCfm(GATT_ADD_DB_CFM_T *p_event_data)
{
    /*Handling signal as per current state */
    switch(g_app_data.state)
    {
        case app_state_init:
        {
            if(p_event_data->result == sys_status_success)
            {
                /* start advertisements. */
                AppSetState(app_state_advertising);
            }
            else
            {
                /* Don't expect this to happen */
                ReportPanic(app_panic_db_registration);
            }
        }
        break;

        default:
            /* Control should never come here */
            ReportPanic(app_panic_invalid_state);
        break;
    }
}


/*---------------------------------------------------------------------------
 *
 *  NAME
 *      handleSignalLmEvConnectionComplete
 *
 *  DESCRIPTION
 *      This function handles the signal LM_EV_CONNECTION_COMPLETE.
 *
 *  RETURNS
 *      Nothing.
 *
 
*----------------------------------------------------------------------------*/

static void handleSignalLmEvConnectionComplete(
                                     LM_EV_CONNECTION_COMPLETE_T *p_event_data)
{
    /* Store the connection parameters. */
    g_app_data.conn_interval = p_event_data->data.conn_interval;
    g_app_data.conn_latency = p_event_data->data.conn_latency;
    g_app_data.conn_timeout = p_event_data->data.supervision_timeout;
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      handleSignalGattCancelConnectCfm
 *
 *  DESCRIPTION
 *      This function handles the signal GATT_CANCEL_CONNECT_CFM
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void handleSignalGattCancelConnectCfm(void)
{
    /* Handling signal as per current state */
    switch(g_app_data.state)
    {
        case app_state_advertising:
        {
            /* stop the advertisements and move to idle state */
            AppSetState(app_state_idle);
        }
        break;
        
        default:
            /* Control should never come here */
            ReportPanic(app_panic_invalid_state);
        break;
    }
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      handleSignalGattConnectCfm
 *
 *  DESCRIPTION
 *      This function handles the signal GATT_CONNECT_CFM
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void handleSignalGattConnectCfm(GATT_CONNECT_CFM_T* p_event_data)
{
    /*Handling signal as per current state */
    switch(g_app_data.state)
    {
        case app_state_advertising:
        {
            if(p_event_data->result == sys_status_success)
            {
                /* Store received UCID */
                g_app_data.st_ucid = p_event_data->cid;

                /* Store connected BD Address */
                g_app_data.con_bd_addr = p_event_data->bd_addr;

                /* Enter connected state */
                AppSetState(app_state_connected);

                /* if the application does not mandate 
                 * encryption requirement on its characteristics, the 
                 * remote master may or may not encrypt the link. Start a 
                 * timer  here to give remote master some time to encrypt 
                 * the link and on expiry of that timer, send a connection 
                 * parameter update request to remote device.
                 */

                /* If the current connection parameters being used don't 
                 * comply with the application's preferred connection 
                 * parameters and the timer is not running and , start timer
                 * to trigger Connection Parameter Update procedure
                 */

                if(g_app_data.con_param_update_tid == TIMER_INVALID)
                {
                    appStartConnUpdateTimer();
                } 
            }
            else
            {
                /* Connection failure - Trigger advertisements */

                /* Already in app_state_advertising state, so just 
                 * trigger advertisements
                 */
                GattStartAdverts();
            }
        }
        break;

        default:
        {
            /* Control should never come here */
            ReportPanic(app_panic_invalid_state);
        }
        break;
    }
}



/*----------------------------------------------------------------------------*
 *  NAME
 *      handleSignalLsConnParamUpdateCfm
 *
 *  DESCRIPTION
 *      This function handles the signal LS_CONNECTION_PARAM_UPDATE_CFM.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void handleSignalLsConnParamUpdateCfm(
                            LS_CONNECTION_PARAM_UPDATE_CFM_T *p_event_data)
{
    /*Handling signal as per current state */
    switch(g_app_data.state)
    {
        case app_state_connected:
        {
            /* Received in response to the L2CAP_CONNECTION_PARAMETER_UPDATE 
             * request sent from the slave after encryption is enabled. If 
             * the request has failed, the device should again send the same 
             * request only after Tgap(conn_param_timeout). Refer 
             * Bluetooth 4.0 spec Vol 3 Part C, Section 9.3.9 and profile spec.
             */
            if ((p_event_data->status != ls_err_none) &&
                    (g_app_data.num_conn_update_req < 
                    MAX_NUM_CONN_PARAM_UPDATE_REQS))
            {
                /* Delete timer if running */
                TimerDelete(g_app_data.con_param_update_tid);

                
                g_app_data.con_param_update_tid = TimerCreate(
                                             GAP_CONN_PARAM_TIMEOUT,
                                             TRUE, requestConnParamUpdate);
            }
        }
        break;

        default:
        {
            /* Control should never come here */
            ReportPanic(app_panic_invalid_state);
        }
        break;
    }
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      handleSignalLsConnParamUpdateInd
 *
 *  DESCRIPTION
 *      This function handles the signal LS_CONNECTION_PARAM_UPDATE_IND.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void handleSignalLsConnParamUpdateInd(
                                 LS_CONNECTION_PARAM_UPDATE_IND_T *p_event_data)
{

    /*Handling signal as per current state */
    switch(g_app_data.state)
    {

        case app_state_connected:
        {
            /* Delete timer if running */
            TimerDelete(g_app_data.con_param_update_tid);
            g_app_data.con_param_update_tid = TIMER_INVALID;

            /* Store the new connection parameters. */
            g_app_data.conn_interval = p_event_data->conn_interval;
            g_app_data.conn_latency = p_event_data->conn_latency;
            g_app_data.conn_timeout = p_event_data->supervision_timeout;
            
            /* Connection parameters have been updated. Check if new parameters 
             * comply with application preferred parameters. If not, application shall 
             * trigger Connection parameter update procedure 
             */
            appStartConnUpdateTimer();
        }
        break;

        default:
        {
            /* Control should never come here */
            ReportPanic(app_panic_invalid_state);
        }
        break;
    }

}


/*----------------------------------------------------------------------------*
 *  NAME
 *      handleSignalGattAccessInd
 *
 *  DESCRIPTION
 *      This function handles GATT_ACCESS_IND message for attributes 
 *      maintained by the application.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void handleSignalGattAccessInd(GATT_ACCESS_IND_T *p_event_data)
{
    /*Handling signal as per current state */
    switch(g_app_data.state)
    {
        case app_state_connected:
        {
            /* Received GATT ACCESS IND with write access */
            if(p_event_data->flags == 
                (ATT_ACCESS_WRITE | 
                 ATT_ACCESS_PERMISSION |
                 ATT_ACCESS_WRITE_COMPLETE))
            {
                HandleAccessWrite(p_event_data);
            }
            /* Received GATT ACCESS IND with read access */
            else if(p_event_data->flags == 
                (ATT_ACCESS_READ | 
                ATT_ACCESS_PERMISSION))
            {
                HandleAccessRead(p_event_data);
            }
            else
            {
                /* No other request is supported */
                GattAccessRsp(p_event_data->cid, p_event_data->handle, 
                              gatt_status_request_not_supported,
                              0, NULL);
            }
        }
        break;

        default:
        {
            /* Control should never come here */
            ReportPanic(app_panic_invalid_state);
        }
        break;
    }
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      handleSignalLmDisconnectComplete
 *
 *  DESCRIPTION
 *      This function handles LM Disconnect Complete event which is received
 *      at the completion of disconnect procedure triggered either by the 
 *      device or remote host or because of link loss 
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void handleSignalLmDisconnectComplete(
                HCI_EV_DATA_DISCONNECT_COMPLETE_T *p_event_data)
{

    /* Set UCID to INVALID_UCID */
    g_app_data.st_ucid = GATT_INVALID_UCID;

    /* Reset the connection parameter variables. */
    g_app_data.conn_interval = 0;
    g_app_data.conn_latency = 0;
    g_app_data.conn_timeout = 0;
    
    /* LM_EV_DISCONNECT_COMPLETE event can have following disconnect 
     * reasons:
     *
     * HCI_ERROR_CONN_TIMEOUT - Link Loss case
     * HCI_ERROR_CONN_TERM_LOCAL_HOST - Disconnect triggered by device
     * HCI_ERROR_OETC_* - Other end (i.e., remote host) terminated connection
     */
    /*Handling signal as per current state */
    switch(g_app_data.state)
    {
        case app_state_connected:
        {
            /* Initialise Application data instance */
            appDataInit();
        }
            /* FALLTHROUGH */

        case app_state_disconnecting:
        {
            /* Start undirected advertisements by moving to 
             * app_state_advertising state
             */
            AppSetState(app_state_advertising);
        }
        break;
        
        default:
        {
            /* Control should never come here */
            ReportPanic(app_panic_invalid_state);
        }
        break;
    }
}

/*============================================================================*
 *  Public Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      ReportPanic
 *
 *  DESCRIPTION
 *      This function calls firmware panic routine and gives a single point 
 *      of debugging any application level panics
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void ReportPanic(app_panic_code panic_code)
{
    /* Raise panic */
    Panic(panic_code);
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      AppHandleShortButtonPress
 *
 *  DESCRIPTION
 *      This function contains handling of short button press. If connected,
 *      the device disconnects from the connected host else it triggers
 *      advertisements
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void AppHandleShortButtonPress(void)
{
    /* Indicate short button press using short beep */
    SoundBuzzer(buzzer_beep_short);

    /* Handling signal as per current state */
    switch(g_app_data.state)
    {
        case app_state_connected:
        {
            /* Disconnect with the connected host*/
            AppSetState(app_state_disconnecting);
            
            /* As per the specification Vendor may choose to initiate the 
             * idle timer which will eventually initiate the disconnect.
             */
        }    
        break;

        case app_state_idle:
        {
            /* Trigger advertisements */
            AppSetState(app_state_advertising);
        }
        break;

        default:
            /* Ignore in remaining states */
        break;

    }

}

/*----------------------------------------------------------------------------*
 *  NAME
 *      AppSetState
 *
 *  DESCRIPTION
 *      This function is used to set the state of the application.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void AppSetState(app_state new_state)
{
    /* Check if the new state to be set is not the same as the present state
     * of the application.
     */
    app_state old_state = g_app_data.state;
    
    if (old_state != new_state)
    {
        /* Handle exiting old state */
        switch (old_state)
        {
            case app_state_init:
            {
                /* Common things to do whenever application exits
                 * app_state_init state.
                 */
            }
            break;

            case app_state_disconnecting:
            {
                /* Common things to do whenever application exits
                 * app_state_disconnecting state.
                 */

                /* Initialise application and used services data structure 
                 * while exiting Disconnecting state
                 */
                appDataInit();
            }
            break;

            case app_state_advertising:
            {
                /* Common things to do whenever application exits
                 * APP_ADVERTISING state.
                 */
                appExitAdvertising();
            }
            break;

            case app_state_connected:
            {
                /* The application may need to maintain the values of some
                 * profile specific data across connections and power cycles.
                 * These values would have changed in 'connected' state. So,
                 * update the values of this data stored in the NVM.
                 */
            }
            break;

            case app_state_idle:
            {
                /* Nothing to do */
            }
            break;

            default:
            {
                /* Nothing to do */
            }
            break;
        }

        /* Set new state */
        g_app_data.state = new_state;

        /* Handle entering new state */
        switch (new_state)
        {
            case app_state_advertising:
            {
                /* Trigger advertisements. */
                GattStartAdverts();

                /* Indicate advertising mode by sounding two short beeps */
                SoundBuzzer(buzzer_beep_twice);
            }
            break;

            case app_state_idle:
            {
                /* Sound long beep to indicate non connectable mode*/
                SoundBuzzer(buzzer_beep_long);
            }
            break;

            case app_state_connected:
            {
                /* Common things to do whenever application enters
                 * app_state_connected state.
                 */
             }
            break;

            case app_state_disconnecting:
            {
                /* Disconnect the link */
                GattDisconnectReq(g_app_data.st_ucid);
            }
            break;

            default:
            break;
        }
    }
}

/*----------------------------------------------------------------------------*
 *  NAME
 *      AppGetState
 *
 *  DESCRIPTION
 *      This function returns the current state of the application. 
 *
 *  RETURNS/MODIFIES
 *      app_state : application state.
 *
 *---------------------------------------------------------------------------*/

extern app_state AppGetState(void)
{
    return g_app_data.state;
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      AppStartAdvertTimer
 *
 *  DESCRIPTION
 *      This function starts the advertisement timer
 *
 *  RETURNS/MODIFIES
 *      None
 *
 *---------------------------------------------------------------------------*/

extern void AppStartAdvertTimer(uint32 interval)
{
    TimerDelete(g_app_data.app_tid);

    if(interval) /* Initiate the timer only if the interval is non-zero */
    {
        /* Start advertisement timer  */
        g_app_data.app_tid = TimerCreate(interval, TRUE, appAdvertTimerHandler);
    }
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      GetConnectionID
 *
 *  DESCRIPTION
 *      This function returns the connection identifier
 *
 *  RETURNS
 *      st_ucid : Connection Id.
 *
 *----------------------------------------------------------------------------*/

extern uint16 GetConnectionID(void)
{
    return g_app_data.st_ucid;
}


/*============================================================================*
 *  System Callback Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      AppPowerOnReset
 *
 *  DESCRIPTION
 *      This function is called just after a power-on reset (including after
 *      a firmware panic).
 *
 *      NOTE: this function should only contain code to be executed after a
 *      power-on reset or panic. Code that should also be executed after an
 *      HCI_RESET should instead be placed in the reset() function.
 *
 *  RETURNS/MODIFIES
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/
void AppPowerOnReset(void)
{
    /* Configure the application constants */
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      AppInit
 *
 *  DESCRIPTION
 *      This function is called after a power-on reset (including after a
 *      firmware panic) or after an HCI Reset has been requested.
 *
 *      NOTE: In the case of a power-on reset, this function is called
 *      after app_power_on_reset.
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void AppInit(sleep_state last_sleep_state)
{
    uint16 gatt_db_length = 0;
    uint16 *p_gatt_db = NULL;

    /* Initialise the hello server application state */
    g_app_data.state = app_state_init;

    /* Initialise the application timers */
    TimerInit(MAX_APP_TIMERS, (void*)app_timers);
 
    /* Initialise GATT entity */
    GattInit();

    /* Install GATT Server support for the optional Write procedure
     * This is mandatory only if control point characteristic is supported. 
     */
    GattInstallServerWrite();

    /* Don't wakeup on UART RX line */
    SleepWakeOnUartRX(FALSE);

    /* Hello service Initialisation on Chip reset */
    HelloServiceInitChipReset();
    
    /* Initialize the gap data */
    GapDataInit();

    /* Tell Security Manager module about the value it needs to initialize it's
     * diversifier to.
     */
    SMInit(0);

    /* Initialise application data structure */
    appDataInit();
    
    /* Initialise hello server H/W */
    AppInitHardware();

    /* Tell GATT about our database. We will get a GATT_ADD_DB_CFM event when
     * this has completed.
     */
    p_gatt_db = GattGetDatabase(&gatt_db_length);

    GattAddDatabaseReq(gatt_db_length, p_gatt_db);
    
    DebugInit(1, UartDataRxCallback, NULL);
    DebugWriteString("Initialized\r\n");
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      AppProcessSystemEvent
 *
 *  DESCRIPTION
 *      This user application function is called whenever a system event, such
 *      as a battery low notification, is received by the system.
 *
 *  RETURNS/MODIFIES
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/
void AppProcessSystemEvent(sys_event_id id, void *data)
{
    switch(id)
    {
        case sys_event_pio_changed:
        {
             /* Handle the PIO changed event. */
             HandlePIOChangedEvent((pio_changed_data*)data);
        }
        break;
            
        default:
            /* Ignore anything else */
        break;
    }
}


/*----------------------------------------------------------------------------*
 *  NAME
 *      AppProcessLmEvent
 *
 *  DESCRIPTION
 *      This user application function is called whenever a LM-specific event is
 *      received by the system.
 *
 *  RETURNS/MODIFIES
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/
bool AppProcessLmEvent(lm_event_code event_code, LM_EVENT_T *p_event_data)
{
    switch (event_code)
    {
        /* Handle events received from Firmware */

        case GATT_ADD_DB_CFM:
        {
            /* Attribute database registration confirmation */
            handleSignalGattAddDbCfm((GATT_ADD_DB_CFM_T*)p_event_data);
        }
        break;

        case LM_EV_CONNECTION_COMPLETE:
        {
            /* Connection Complete meta event */
            handleSignalLmEvConnectionComplete(
                                (LM_EV_CONNECTION_COMPLETE_T*)p_event_data);
        }
        break;

        case GATT_CANCEL_CONNECT_CFM:
        {
            /* Confirmation for the completion of GattCancelConnectReq()
             * procedure 
             */
            handleSignalGattCancelConnectCfm();
        }
        break;

        case GATT_CONNECT_CFM:
        {
            /* Confirmation for the completion of GattConnectReq() 
             * procedure
             */
            handleSignalGattConnectCfm((GATT_CONNECT_CFM_T*)p_event_data);
        }
        break;

        /* Received in response to the LsConnectionParamUpdateReq() 
         * request sent from the slave after encryption is enabled. If 
         * the request has failed, the device should again send the same 
         * request only after Tgap(conn_param_timeout). Refer Bluetooth 4.0 
         * spec Vol 3 Part C, Section 9.3.9 and HID over GATT profile spec 
         * section 5.1.2.
         */
        case LS_CONNECTION_PARAM_UPDATE_CFM:
        {
            /* This event is raised after a call to LsSetNewConnectionParamReq() 
             * when the Connection Parameter Update procedure has finished
             */
            handleSignalLsConnParamUpdateCfm((LS_CONNECTION_PARAM_UPDATE_CFM_T*)
                p_event_data);
        }
        break;

        case LS_CONNECTION_PARAM_UPDATE_IND:
        {
            /* Indicates completion of remotely triggered Connection 
             * parameter update procedure
             */
            handleSignalLsConnParamUpdateInd(
                            (LS_CONNECTION_PARAM_UPDATE_IND_T *)p_event_data);
        }
        break;

        case GATT_ACCESS_IND:
        {
            /* Indicates that an attribute controlled directly by the
             * application (ATT_ATTR_IRQ attribute flag is set) is being 
             * read from or written to.
             */
            handleSignalGattAccessInd((GATT_ACCESS_IND_T *)p_event_data);
        }
        break;

        case GATT_DISCONNECT_IND:
        {
            /* Disconnect procedure triggered by remote host or due to 
             * link loss is considered complete on reception of 
             * LM_EV_DISCONNECT_COMPLETE event. So, it gets handled on 
             * reception of LM_EV_DISCONNECT_COMPLETE event.
             */
        }
        break;

        case GATT_DISCONNECT_CFM:
        {
            /* Confirmation for the completion of GattDisconnectReq()
             * procedure is ignored as the procedure is considered complete 
             * on reception of LM_EV_DISCONNECT_COMPLETE event. So, it gets 
             * handled on reception of LM_EV_DISCONNECT_COMPLETE event.
             */
        }
        break;

        case LM_EV_DISCONNECT_COMPLETE:
        {
            /* Disconnect procedures either triggered by application or remote
             * host or link loss case are considered completed on reception 
             * of LM_EV_DISCONNECT_COMPLETE event
             */
             handleSignalLmDisconnectComplete(
                    &((LM_EV_DISCONNECT_COMPLETE_T *)p_event_data)->data);
        }
        break;

        default:
        {
            /* Ignore any other event */ 
        }
        break;

    }

    return TRUE;
}

/****************************************************************************
NAME
    UartDataRxCallback

DESCRIPTION
    This callback is issued when data is received over UART. Application
    may ignore the data, if not required. For more information refer to
    the API documentation for the type "uart_data_out_fn"

RETURNS
    The number of words processed, return data_count if all of the received
    data had been processed (or if application don't care about the data)
*/
static uint16 UartDataRxCallback ( void* p_data, uint16 data_count,
        uint16* p_num_additional_words )
{
    *p_num_additional_words = 0; /* Application do not need any additional
                                       data to be received */
    return data_count;
}
