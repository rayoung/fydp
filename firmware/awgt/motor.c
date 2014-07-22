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
    /* motor initially idle */
    MotorSetVelocity(TRUE, 0);
    
    /* PWM0 is bugged, use 1,2 or 3 instead */
    PioSetMode(PIO_MOTOR1, pio_mode_pwm1);
    PioSetDir(PIO_MOTOR1, TRUE);
    PioSetPullModes((1UL << PIO_MOTOR1), pio_mode_strong_pull_up);
    
    PioSetMode(PIO_MOTOR2, pio_mode_pwm2);
    PioSetDir(PIO_MOTOR2, TRUE);
    PioSetPullModes((1UL << PIO_MOTOR2), pio_mode_strong_pull_up);
    
    PioSetMode(PIO_MOTOR_nENABLE, pio_mode_user);
    PioSetDir(PIO_MOTOR_nENABLE, TRUE);
    PioSetPullModes((1UL << PIO_MOTOR_nENABLE), pio_mode_no_pulls);
    PioSet(PIO_MOTOR_nENABLE, FALSE);
    
    MotorEnableSleep(FALSE);
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
    if (ccw)
    {
        PioConfigPWM(1, pio_pwm_mode_push_pull, 255, 0, 255, 255, 0, 255, 0);
        PioConfigPWM(2, pio_pwm_mode_push_pull, 255-duty, duty, 255, 255-duty, duty, 255, 0);
    }
    else
    {
        PioConfigPWM(2, pio_pwm_mode_push_pull, 255, 0, 255, 255, 0, 255, 0);
        PioConfigPWM(1, pio_pwm_mode_push_pull, 255-duty, duty, 255, 255-duty, duty, 255, 0);
    }
}

/*----------------------------------------------------------------------------*
 *  NAME
 *      MotorEnableSleep
 *
 *  DESCRIPTION
 *      This function puts the motor driver to sleep
 *
 *  RETURNS/MODIFIES
 *      Nothing
 *
 *---------------------------------------------------------------------------*/

extern void MotorEnableSleep(bool enable)
{
    PioSet(PIO_MOTOR_nENABLE, !enable);
    
    /* toggle PWM interfaces */
    PioEnablePWM(1, !enable);
    PioEnablePWM(2, !enable);
}