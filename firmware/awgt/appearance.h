/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2012-2013
 *
 * FILE
 *     appearance.h
 *
 *  DESCRIPTION
 *     This file defines macros for commonly used appearance values, which are 
 *     defined by BT SIG.
 *
 *****************************************************************************/

#ifndef __APPEARANCE_H__
#define __APPEARANCE_H__

/*=====================================================*
 *  Public Definitions
 *=====================================================*/

/* Brackets should not be used around the value of a macro. The parser 
 * which creates .c and .h files from .db file doesn't understand  brackets 
 * and will raise syntax errors.
 */

/* For values, refer http://developer.bluetooth.org/gatt/characteristics/Pages/
 * CharacteristicViewer.aspx?u=org.bluetooth.characteristic.gap.appearance.xml
 */

/*Unknown appearance value*/
#define APPEARANCE_UNKNOWN_VALUE                0x0000

#define APPEARANCE_GATT_SERVER_VALUE            APPEARANCE_UNKNOWN_VALUE

#define APPEARANCE_APPLICATION_VALUE            APPEARANCE_GATT_SERVER_VALUE

#endif /* __APPEARANCE_H__ */
