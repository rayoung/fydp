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

#define PIO_MOTOR1                  10

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
    /* PWM0 is bugged, use 1,2 or 3 instead */
    uint8 level = 192;
    uint8 max = 255;
    if (PioConfigPWM(1, pio_pwm_mode_push_pull, level, max-level, 0, level, max-level, 0, 0))
    {     
        PioEnablePWM(1, TRUE);
    }
    
    /* Connect PWM1 to PIO10 */
    PioSetMode(PIO_MOTOR1, pio_mode_pwm1);
    PioSetDir(PIO_MOTOR1, TRUE);
    PioSetPullModes((1UL << PIO_MOTOR1), pio_mode_strong_pull_up);
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

