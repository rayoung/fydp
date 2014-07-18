/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      app_hw.c
 *
 *  DESCRIPTION
 *      This file defines the application hardware specific routines.
 *
 *****************************************************************************/

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <pio.h>
#include <pio_ctrlr.h>

/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "app_hw.h"
#include "app_main.h"
#include "buzzer.h"
#include "app_gatt.h"

/*============================================================================*
 *  Private Definitions
 *============================================================================*/

/* Setup PIO 11 as Button PIO */
#define BUTTON_PIO                  (11)

#define BUTTON_PIO_MASK             (PIO_BIT_MASK(BUTTON_PIO))

/*============================================================================*
 *  Public Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      AppInitHardware
 *
 *  DESCRIPTION
 *      This function is called to initialise the application hardware
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void AppInitHardware(void)
{
    /* Setup PIOs
     * PIO11 - Button
     */
    /* Set the button PIO to user mode */
    PioSetModes(BUTTON_PIO_MASK, pio_mode_user);

    /* Set the PIO direction as input. */
    PioSetDir(BUTTON_PIO, PIO_DIRECTION_INPUT);

    /* Pull up the PIO. */
    PioSetPullModes(BUTTON_PIO_MASK, pio_mode_strong_pull_up);

    /* Initialize Buzzer Hardware */
    BuzzerInitHardware();

    /* Setup button on PIO11 */
    PioSetEventMask(BUTTON_PIO_MASK, pio_event_mode_both);

    /* Save power by changing the I2C pull mode to pull down.*/
    PioSetI2CPullMode(pio_i2c_pull_mode_strong_pull_down);

}


/*----------------------------------------------------------------------------*
 *  NAME
 *      HandlePIOChangedEvent
 *
 *  DESCRIPTION
 *      This function handles PIO Changed event
 *
 *  RETURNS
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

extern void HandlePIOChangedEvent(pio_changed_data *pio_data)
{

    if(pio_data->pio_cause & BUTTON_PIO_MASK)
    {
        /* PIO changed */
        uint32 pios = PioGets();

        if(!(pios & BUTTON_PIO_MASK))
        {
            /* This event comes when a button is pressed */
        }
        else
        {
            /* Indicate short button press using short beep */
            SoundBuzzer(buzzer_beep_short);

            AppHandleShortButtonPress();

        }
    }
}

