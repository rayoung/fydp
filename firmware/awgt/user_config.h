/*******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 * FILE
 *      user_config.h
 *
 * DESCRIPTION
 *      This file contains definitions which will enable customization of the
 *      application.
 *
 ******************************************************************************/

#ifndef __USER_CONFIG_H__
#define __USER_CONFIG_H__

/*=============================================================================*
 *  Public Definitions
 *============================================================================*/

/* This macro defines the Advertisement timer, on expiry of which application
 * enters idle state. For disabling the expiry of this timer, define it to be 
 * zero.
 */
#define CONNECTION_ADVERT_TIMEOUT_VALUE  (5 * MINUTE)

#endif /* __USER_CONFIG_H__ */
