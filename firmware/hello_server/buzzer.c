/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 *  FILE
 *      buzzer.c
 *
 *  DESCRIPTION
 *      This file defines routines for accessing buzzer functionality.
 *
 *****************************************************************************/

/*============================================================================*
 *  Local Header Files
 *============================================================================*/

#include "buzzer.h"

#ifdef ENABLE_BUZZER
#include "app_hw.h"
#include "app_main.h"

/*============================================================================*
 *  SDK Header Files
 *============================================================================*/

#include <pio.h>
#include <pio_ctrlr.h>

/*============================================================================*
 *  Private Definitions
 *============================================================================*/

/* Setup PIOs
 *  PIO3    Buzzer
 */
#define BUZZER_PIO              (3)

#define BUZZER_PIO_MASK         (PIO_BIT_MASK(BUZZER_PIO))

/* The index (0-3) of the PWM unit to be configured */
#define BUZZER_PWM_INDEX_0      (0)

/* PWM parameters for Buzzer */

/* Dull on. off and hold times */
#define DULL_BUZZ_ON_TIME       (2)    /* 60us */
#define DULL_BUZZ_OFF_TIME      (15)   /* 450us */
#define DULL_BUZZ_HOLD_TIME     (0)

/* Bright on, off and hold times */
#define BRIGHT_BUZZ_ON_TIME     (2)    /* 60us */
#define BRIGHT_BUZZ_OFF_TIME    (15)   /* 450us */
#define BRIGHT_BUZZ_HOLD_TIME   (0)    /* 0us */

#define BUZZ_RAMP_RATE          (0xFF)

/* TIMER values for Buzzer */
#define SHORT_BEEP_TIMER_VALUE  (100* MILLISECOND)
#define LONG_BEEP_TIMER_VALUE   (500* MILLISECOND)
#define BEEP_GAP_TIMER_VALUE    (25* MILLISECOND)

/*============================================================================*
 *  Public data
 *============================================================================*/

/* Buzzer data instance */
BUZZER_DATA_T                   g_buzz_data;

/*============================================================================*
 *  Private Function Prototypes
 *===========================================================================*/

static void appBuzzerTimerHandler(timer_id tid);

/*============================================================================*
 *  Private Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      appBuzzerTimerHandler
 *
 *  DESCRIPTION
 *      This function is used to stop the Buzzer at the expiry of 
 *      timer.
 *
 *  RETURNS/MODIFIES
 *      Nothing.
 *
 *---------------------------------------------------------------------------*/

static void appBuzzerTimerHandler(timer_id tid)
{
    uint32 beep_timer = SHORT_BEEP_TIMER_VALUE;

    g_buzz_data.buzzer_tid = TIMER_INVALID;

    switch(g_buzz_data.beep_type)
    {
        case buzzer_beep_short: /* FALLTHROUGH */
        case buzzer_beep_long:
        {
            g_buzz_data.beep_type = buzzer_beep_off;

            /* Disable buzzer */
            PioEnablePWM(BUZZER_PWM_INDEX_0, FALSE);
        }
        break;

        case buzzer_beep_twice:
        {
            if(g_buzz_data.beep_count == 0)
            {
                /* First beep sounded. Increment the beep count and start the
                 * silent gap.
                 */
                g_buzz_data.beep_count = 1;

                /* Disable buzzer */
                PioEnablePWM(BUZZER_PWM_INDEX_0, FALSE);

                /* Time gap between two beeps */
                beep_timer = BEEP_GAP_TIMER_VALUE;
            }
            else if(g_buzz_data.beep_count == 1)
            {
                /* Soound the second beep and increment the beep count */

                g_buzz_data.beep_count = 2;

                /* Enable buzzer */
                PioEnablePWM(BUZZER_PWM_INDEX_0, TRUE);

                /* Start another short beep */
                beep_timer = SHORT_BEEP_TIMER_VALUE;
            }
            else
            {
                /* Two beeps have been sounded. Stop buzzer now and reset the 
                 * beep count.
                 */
                g_buzz_data.beep_count = 0;

                /* Disable buzzer */
                PioEnablePWM(BUZZER_PWM_INDEX_0, FALSE);

                g_buzz_data.beep_type = buzzer_beep_off;
            }
        }
        break;

        case buzzer_beep_thrice:
        {
            if(g_buzz_data.beep_count == 0 ||
               g_buzz_data.beep_count == 2)
            {
                /* Start the silent gap*/
                ++ g_buzz_data.beep_count;

                /* Disable buzzer */
                PioEnablePWM(BUZZER_PWM_INDEX_0, FALSE);

                /* Time gap between two beeps */
                beep_timer = BEEP_GAP_TIMER_VALUE;
            }
            else if(g_buzz_data.beep_count == 1 ||
                    g_buzz_data.beep_count == 3)
            {
                /* Start the beep sounding part. */
                ++ g_buzz_data.beep_count;

                /* Enable buzzer */
                PioEnablePWM(BUZZER_PWM_INDEX_0, TRUE);

                beep_timer = SHORT_BEEP_TIMER_VALUE;
            }
            else
            {
                /* Two beeps have been sounded. Stop buzzer now*/
                g_buzz_data.beep_count = 0;

                /* Disable buzzer */
                PioEnablePWM(BUZZER_PWM_INDEX_0, FALSE);

                g_buzz_data.beep_type = buzzer_beep_off;
            }
        }
        break;

        default:
        {
            /* No such beep type */
            ReportPanic(app_panic_unexpected_beep_type);
            /* Though break statement will not be executed after panic but this
             * has been kept to avoid any confusion for default case.
             */
        }
        break;
    }

    if(g_buzz_data.beep_type != buzzer_beep_off)
    {
        /* start the timer */
        g_buzz_data.buzzer_tid = TimerCreate(beep_timer, TRUE, 
                                               appBuzzerTimerHandler);
    }
}


/*============================================================================*
 *  Public Function Implementations
 *============================================================================*/

/*----------------------------------------------------------------------------*
 *  NAME
 *      BuzzerInitHardware
 *
 *  DESCRIPTION
 *      This function initializes the buzzer hardware 
 *
 *  RETURNS/MODIFIES
 *      Nothing
 *
 *---------------------------------------------------------------------------*/

extern void BuzzerInitHardware(void)
{
    /* Configure the buzzer pio to use PWM. */
    PioSetModes(BUZZER_PIO_MASK, pio_mode_pwm0);

    /* Configure the PWM for buzzer ON OFF values */
    PioConfigPWM(BUZZER_PWM_INDEX_0, pio_pwm_mode_push_pull, DULL_BUZZ_ON_TIME,
                 DULL_BUZZ_OFF_TIME, DULL_BUZZ_HOLD_TIME, BRIGHT_BUZZ_ON_TIME,
                 BRIGHT_BUZZ_OFF_TIME, BRIGHT_BUZZ_HOLD_TIME, BUZZ_RAMP_RATE);

    /* Disable buzzer for the time being. */
    PioEnablePWM(BUZZER_PWM_INDEX_0, FALSE);

}

/*----------------------------------------------------------------------------*
 *  NAME
 *      SoundBuzzer
 *
 *  DESCRIPTION
 *      This function is called to trigger beeps of different types 
 *      'buzzer_beep_type'
 *
 *  RETURNS/MODIFIES
 *      Nothing
 *
 *---------------------------------------------------------------------------*/

extern void SoundBuzzer(buzzer_beep_type beep_type)
{

    uint32 beep_timer = SHORT_BEEP_TIMER_VALUE;

    /* Disable the buzzer and stop the buzzer timer. */
    PioEnablePWM(BUZZER_PWM_INDEX_0, FALSE);

    TimerDelete(g_buzz_data.buzzer_tid);
    g_buzz_data.buzzer_tid = TIMER_INVALID;

    g_buzz_data.beep_count = 0;

    /* Store the beep type in some global variable. It will be used on timer 
     * expiry to check the type of beeps being sounded.
     */
    g_buzz_data.beep_type = beep_type;

    switch(g_buzz_data.beep_type)
    {
        case buzzer_beep_off:
        {
            /* Don't do anything */
        }
        break;

        case buzzer_beep_short: /* One short beep will be sounded */
            /* FALLTHROUGH */
        case buzzer_beep_twice: /* Two short beeps will be sounded */
            /* FALLTHROUGH */
        case buzzer_beep_thrice: /* Three short beeps will be sounded */
        {
            beep_timer = SHORT_BEEP_TIMER_VALUE;
        }
        break;

        case buzzer_beep_long:
        {
            /* One long beep will be sounded */
            beep_timer = LONG_BEEP_TIMER_VALUE;
        }
        break;

        default:
        {
            /* No such beep type defined */
            ReportPanic(app_panic_unexpected_beep_type);

            /* Though break statement will not be executed after panic but this
             * has been kept to avoid any confusion for default case.
             */
        }
        break;
    }

    if(g_buzz_data.beep_type != buzzer_beep_off)
    {
        /* Initialize beep count to zero */
        g_buzz_data.beep_count = 0;

        /* Enable buzzer */
        PioEnablePWM(BUZZER_PWM_INDEX_0, TRUE);

        /* start the buzzer timer */
        TimerDelete(g_buzz_data.buzzer_tid);
        g_buzz_data.buzzer_tid = TimerCreate(beep_timer, TRUE, 
                                             appBuzzerTimerHandler);
    }

}

#endif /* ENABLE_BUZZER */
