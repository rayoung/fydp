/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      motor.h
 *
 *  DESCRIPTION
 *      This file contains prototypes for accessing motor functionality.
 *
 *****************************************************************************/

#ifndef __MOTOR_H__
#define __MOTOR_H__

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <types.h>

/*============================================================================*
 *  Public Data Declarations
 *============================================================================*/

#define PIO_MOTOR1              9

#define PIO_MOTOR2              10

#define PIO_MOTOR_nENABLE       11

/*============================================================================*
 *  Public Function Prototypes
 *============================================================================*/

/* This function initializes the motor hardware */
extern void MotorInitHardware(void);

/* This function sets the motor speed and direction */
extern void MotorSetVelocity(bool ccw, uint8 duty);

/* This function puts the motor driver to sleep */
extern void MotorEnableSleep(bool enable);

#endif /* __MOTOR_H__ */

