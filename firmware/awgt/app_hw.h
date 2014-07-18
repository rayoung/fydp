/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      app_hw.h
 *
 *  DESCRIPTION
 *      Header definitions for HW setup.
 *
 *****************************************************************************/

#ifndef __APP_HW_H__
#define __APP_HW_H__

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <types.h>
#include <bluetooth.h>
#include <timer.h>
#include <sys_events.h>

/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "app_gatt.h"

/*============================================================================*
 *  Public Definitions
 *============================================================================*/

/* PIO Bit Mask */
#define PIO_BIT_MASK(pio)       (0x01UL << (pio))

/* PIO direction */
#define PIO_DIRECTION_INPUT     (FALSE)
#define PIO_DIRECTION_OUTPUT    (TRUE)

/* PIO state */
#define PIO_STATE_HIGH          (TRUE)
#define PIO_STATE_LOW           (FALSE)

/*============================================================================*
 *  Public Function Prototypes
 *============================================================================*/

/* This function is called to initialise the application hardware */
extern void AppInitHardware(void);

/* This function handles PIO Changed event */
extern void HandlePIOChangedEvent(pio_changed_data *pio_data);

#endif /* __APP_HW_H__ */
