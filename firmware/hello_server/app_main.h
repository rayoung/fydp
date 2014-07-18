/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      app_main.h
 *
 *  DESCRIPTION
 *      Header file for a simple hello server application.
 *
 ******************************************************************************/

#ifndef __APP_MAIN_H__
#define __APP_MAIN_H__

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <types.h>
#include <bluetooth.h>
#include <timer.h>

/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "app_gatt.h"

/*============================================================================*
 *  Public Function Prototypes
 *============================================================================*/
/* This function calls firmware panic routine and gives a single point 
 * of debugging any application level panics
 */
extern void ReportPanic(app_panic_code panic_code);

/* This function contains handling of short button press. If connected,
 * the device disconnects from the connected host else it triggers
 * advertisements
 */
extern void AppHandleShortButtonPress(void);

/* This function changes the current state of the application */
extern void AppSetState(app_state new_state);

/* This function returns the current state of the application.*/
extern app_state AppGetState(void);

/* This function starts the advertisement timer. */
extern void AppStartAdvertTimer(uint32 interval);

/* Returns the unique connection ID of the connection */
extern uint16 GetConnectionID(void);

#endif /* __APP_MAIN_H__ */
