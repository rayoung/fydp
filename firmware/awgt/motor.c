/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      motor.c
 *
 *  DESCRIPTION
 *      This file defines routines for accessing motor functionality.
 *
 *****************************************************************************/

/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "motor.h"

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <pio.h>

/*============================================================================*
 *  Private Definitions
 *============================================================================*/

/*============================================================================*
 *  Public data
 *============================================================================*/

/*============================================================================*
 *  Private Function Prototypes
 *===========================================================================*/

/*============================================================================*
 *  Private Function Implementations
 *============================================================================*/

/*============================================================================*
 *  Public Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      MotorInitHardware
 *
 *  DESCRIPTION
 *      This function initializes the motor hardware 
 *
 *  RETURNS/MODIFIES
 *      Nothing
 *
 *---------------------------------------------------------------------------*/

extern void MotorInitHardware(void)
{
    /* PWM0 is bugged, use 1,2 or 3 instead */
    MotorSetVelocity(TRUE, 0);
    PioEnablePWM(1, TRUE);
    
    /* Connect PWM1 to motor PIOs */
    PioSetMode(PIO_MOTOR1, pio_mode_pwm1);
    PioSetDir(PIO_MOTOR1, TRUE);
    PioSetPullModes((1UL << PIO_MOTOR1), pio_mode_strong_pull_up);
}

/*----------------------------------------------------------------------------*
 *  NAME
 *      MotorSetVelocity
 *
 *  DESCRIPTION
 *      This function sets the motor speed and direction 
 *
 *  RETURNS/MODIFIES
 *      Nothing
 *
 *---------------------------------------------------------------------------*/

extern void MotorSetVelocity(bool ccw, uint8 duty)
{
    PioConfigPWM(1, pio_pwm_mode_push_pull, duty, 255-duty, 0, duty, 255-duty, 0, 0);
}
