/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      buzzer.h
 *
 *  DESCRIPTION
 *      This file contains prototypes for accessing buzzer functionality.
 *
 *****************************************************************************/

#ifndef __BUZZER_H__
#define __BUZZER_H__

/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "user_config.h"

#ifdef ENABLE_BUZZER

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <types.h>
#include <bluetooth.h>
#include <timer.h>

/*============================================================================*
 *  Public data type
 *============================================================================*/

/* Data type for different type of buzzer beeps */
typedef enum
{
    /* No beeps */
    buzzer_beep_off = 0,

    /* Short beep */
    buzzer_beep_short,

    /* Long beep */
    buzzer_beep_long,

    /* Two short beeps */
    buzzer_beep_twice,

    /* Three short beeps */
    buzzer_beep_thrice

}buzzer_beep_type;


/* Buzzer data structure */
typedef struct
{

    /* Buzzer timer id */
    timer_id                    buzzer_tid;

    /* Variable for storing beep type.*/
    buzzer_beep_type            beep_type;

    /* Variable for keeping track of beep counts. This variable will be 
     * initialized to 0 on beep start and will incremented at every beep 
     * sound
     */
    uint16                      beep_count;

}BUZZER_DATA_T;

/*============================================================================*
 *  Public Data Declarations
 *============================================================================*/

/* Buzzer data instance */
extern BUZZER_DATA_T            g_buzz_data;

/*============================================================================*
 *  Public Function Prototypes
 *============================================================================*/

/* This function initializes the buzzer hardware */
extern void BuzzerInitHardware(void);

/* This function is called to trigger beeps of different types 
 * 'buzzer_beep_type'
 */
extern void SoundBuzzer(buzzer_beep_type beep_type);

#else /* ENABLE_BUZZER */

/* Define buzzer functions to expand to nothing as Buzzer functionality is not 
 * enabled 
 */

#define BuzzerInitHardware()

#define SoundBuzzer(beep_type)

#endif /* ENABLE_BUZZER */

#endif /* __BUZZER_H__ */

