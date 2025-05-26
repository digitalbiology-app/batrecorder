#ifndef __USB_DESCRIPTORS_C
#define __USB_DESCRIPTORS_C

/** INCLUDES *******************************************************/
#include "\Include\usb.h"
#include "\Include\usb_function_audio.h"
#include "\Include\usb_function_hid.h"


/** CONSTANTS ******************************************************/
#if defined(__18CXX)
#pragma romdata
#endif

/* USB Microphone Device Descriptor */
ROM USB_DEVICE_DESCRIPTOR device_dsc =
{
   sizeof(USB_DEVICE_DESCRIPTOR),    // Size of this descriptor in bytes
   USB_DESCRIPTOR_DEVICE,            // DEVICE descriptor type
   0x0110,                 // USB Spec Release Number in BCD format
   //0x0200,                 // USB Spec Release Number in BCD format
   0x00,                   // Class Code
   0x00,                   // Subclass code
   0x00,                   // Protocol code
   USB_EP0_BUFF_SIZE,      // Max packet size for EP0
   0x04D8,                 // Vendor ID
   0x0066,                 // Product ID: Audio Microphone example
   0x0002,                 // Device release number in BCD format
   0x01,                   // Manufacturer string index
   0x02,                   // Product string index
   0x00,                   // Device serial number string index
   0x01                    // Number of possible configurations
};

/* Configuration 1 Descriptor */
ROM BYTE configDescriptor1[] = {

    /* USB Microphone Configuration Descriptor */
    0x09,//sizeof(USB_CFG_DSC),    // Size of this descriptor in bytes
    USB_DESCRIPTOR_CONFIGURATION,                // CONFIGURATION descriptor type
    0x84,0x00,            	// Total length of data for this cfg //shijas
    3,                      // Number of interfaces in this cfg
    1,                      // Index value of this configuration
    0,                      // Configuration string index
    _DEFAULT | _SELF,       // Attributes, see usb_device.h
    50,                     // Max power consumption (2X mA)

    /* USB Microphone Standard AC Interface Descriptor	*/
    0x09,//sizeof(USB_INTF_DSC),   // Size of this descriptor in bytes
    USB_DESCRIPTOR_INTERFACE,      // INTERFACE descriptor type
    AUDIO_CONTROL_INTERFACE_ID,    // Interface Number
    0x00,                   	   // Alternate Setting Number
    0x00,                   	   // Number of endpoints in this intf
    AUDIO_DEVICE,      			   // Class code
    AUDIOCONTROL,				   // Subclass code
    0x00,  						   // Protocol code
    0x00,                      	   // Interface string index


    /* USB Microphone Class-specific AC Interface Descriptor  */
	0x09,						  // Size of this descriptor, in bytes.
	CS_INTERFACE,				  // CS_INTERFACE Descriptor Type
	HEADER,						  // HEADER descriptor subtype
	0x00,0x01,					  // Audio Device compliant to the USB Audio specification version 1.00
	0x1E,0x00,					  // Total number of bytes returned for the class-specific AudioControl interface descriptor.
								  // Includes the combined length of this descriptor header and all Unit and Terminal descriptors.
	0x01,						  // The number of AudioStreaming interfaces in the Audio Interface Collection to which this AudioControl interface belongs
	0x01,						  // AudioStreaming interface 1 belongs to this AudioControl interface.


	/*USB Microphone Input Terminal Descriptor */
	0x0C,						  // Size of the descriptor, in bytes
	CS_INTERFACE,				  // CS_INTERFACE Descriptor Type
	INPUT_TERMINAL,				  // INPUT_TERMINAL descriptor subtype
	ID_INPUT_TERMINAL,			  // ID of this Terminal.
	MICROPHONE,                   // Terminal is Microphone (0x01,0x02) 				  
	0x00,						  // No association
	0x01,						  // One channel
	0x00,0x00,					  // Mono sets no position bits
	0x00,						  // Unused.
	0x00,						  // Unused.

	/* USB Microphone Output Terminal Descriptor */
	0x09,						  // Size of the descriptor, in bytes (bLength)
	CS_INTERFACE,				  // CS_INTERFACE Descriptor Type (bDescriptorType)
	OUTPUT_TERMINAL,			  // OUTPUT_TERMINAL descriptor subtype (bDescriptorSubtype)
	ID_OUTPUT_TERMINAL,			  // ID of this Terminal. (bTerminalID)
	USB_STREAMING,			      // USB Streaming. (wTerminalType
	0x00,						  // unused			(bAssocTerminal)
	ID_INPUT_TERMINAL,	          // From Input Terminal.(bSourceID)
	0x00,						  // unused  (iTerminal)

	/* USB Microphone Standard AS Interface Descriptor (Alt. Set. 0) */
	0x09,						  // Size of the descriptor, in bytes (bLength)
	USB_DESCRIPTOR_INTERFACE,	  // INTERFACE descriptor type (bDescriptorType)
	AUDIO_STREAMING_INTERFACE_ID, // Index of this interface. (bInterfaceNumber)
	0x00,						  // Index of this alternate setting. (bAlternateSetting)
	0x00,						  // 0 endpoints.	(bNumEndpoints)
	AUDIO_DEVICE,				  // AUDIO (bInterfaceClass)
	AUDIOSTREAMING,				  // AUDIO_STREAMING (bInterfaceSubclass)
	0x00,						  // Unused. (bInterfaceProtocol)
	0x00,						  // Unused. (iInterface)

	/* USB Microphone Standard AS Interface Descriptor (Alt. Set. 1) */
	0x09,				          // Size of the descriptor, in bytes (bLength)
	USB_DESCRIPTOR_INTERFACE,     // INTERFACE descriptor type (bDescriptorType)
	AUDIO_STREAMING_INTERFACE_ID, // Index of this interface. (bInterfaceNumber)
	0x01,						  // Index of this alternate setting. (bAlternateSetting)
	0x01,					 	  // 1 endpoint	(bNumEndpoints)
	AUDIO_DEVICE,				  // AUDIO (bInterfaceClass)
	AUDIOSTREAMING,				  // AUDIO_STREAMING (bInterfaceSubclass)
	0x00,					      // Unused. (bInterfaceProtocol)
	0x00,						  // Unused. (iInterface)

	/*  USB Microphone Class-specific AS General Interface Descriptor */
	0x07, 						  // Size of the descriptor, in bytes (bLength)
	CS_INTERFACE,				  // CS_INTERFACE Descriptor Type (bDescriptorType)
	AS_GENERAL,					  // GENERAL subtype (bDescriptorSubtype)
	ID_OUTPUT_TERMINAL,			  // Unit ID of the Output Terminal.(bTerminalLink)
	0x01,						  // Interface delay. (bDelay)
	0x01,0x00,					  // PCM Format (wFormatTag)

	/*  USB Microphone Type I Format Type Descriptor */
	0x0B,						 // Size of the descriptor, in bytes (bLength)
	CS_INTERFACE,				 // CS_INTERFACE Descriptor Type (bDescriptorType)
	FORMAT_TYPE,				 // FORMAT_TYPE subtype. (bDescriptorSubtype)
	0x01,						 // FORMAT_TYPE_I. (bFormatType)
	0x01,						 // One channel.(bNrChannels)
	0x02,						 // Two bytes per audio subframe.(bSubFrameSize)
	0x10,						 // 16 bits per sample.(bBitResolution)
	0x01,						 // One frequency supported. (bSamFreqType)
	0x40,0x1F,0x00,				 // 8000Hz. (tSamFreq)

	/*  USB Microphone Standard Endpoint Descriptor */
	0x09,					    // Size of the descriptor, in bytes (bLength)
	0x05,						// ENDPOINT descriptor (bDescriptorType)
	0x82,						// IN Endpoint 1. (bEndpointAddress)
    0x01,						// Isochronous, not shared. (bmAttributes)
	0x10,0x00,					// 16 bytes per packet (wMaxPacketSize)
	0x01,						// One packet per frame.(bInterval)
	0x00,						// Unused. (bRefresh)
	0x00,						// Unused. (bSynchAddress)

	/* USB Microphone Class-specific Isoc. Audio Data Endpoint Descriptor*/
	0x07,						// Size of the descriptor, in bytes (bLength)
	CS_ENDPOINT,				// CS_ENDPOINT Descriptor Type (bDescriptorType)
	AS_GENERAL,					// GENERAL subtype. (bDescriptorSubtype)
	0x00,						// No sampling frequency control, no pitch control, no packet padding.(bmAttributes)
	0x00,						// Unused. (bLockDelayUnits)
	0x00,0x00,				// Unused. (wLockDelay)

	/* HID Interface */
    /// Interface Descriptor ///
    0x09,//sizeof(USB_INTF_DSC),   	// Size of this descriptor in bytes
    USB_DESCRIPTOR_INTERFACE,     	// INTERFACE descriptor type
    2,                      		// Interface Number
    0,                      		// Alternate Setting Number
    2,                      		// Number of endpoints in this intf
    HID_INTF,               		// Class code
    0,     							// Subclass code
    0,     							// Protocol code
    0,                      		// Interface string index

    /// HID Class-Specific Descriptor ///
    0x09,//sizeof(USB_HID_DSC)+3,  	// Size of this descriptor in bytes
    DSC_HID,                		// HID descriptor type
    0x11,0x01,              		// HID Spec Release Number in BCD format (1.11)
    0x00,                   		// Country Code (0x00 for Not supported)
    HID_NUM_OF_DSC,         		// Number of class descriptors, see usbcfg.h
    DSC_RPT,                		// Report descriptor type
    HID_RPT01_SIZE,0x00,			// sizeof(hid_rpt01),      // Size of the report descriptor
    
    /// Endpoint Descriptor ///
    0x07, // sizeof(USB_EP_DSC)
    USB_DESCRIPTOR_ENDPOINT,    	// Endpoint Descriptor
    HID_EP | _EP_IN,            	// EndpointAddress
    _INTERRUPT,                 	// Attributes
    0x40,0x00,                  	// size
    0x01,                       	// Interval

    /// Endpoint Descriptor ///
    0x07, // sizeof(USB_EP_DSC)
    USB_DESCRIPTOR_ENDPOINT,    	// Endpoint Descriptor
    HID_EP | _EP_OUT,           	// EndpointAddress
    _INTERRUPT,                 	// Attributes
    0x40,0x00,                  	// size
    0x01                        	// Interval
};

//Language code string descriptor
ROM struct{BYTE bLength;BYTE bDscType;WORD string[1];}sd000={
sizeof(sd000),USB_DESCRIPTOR_STRING,{0x0409
}};

//Manufacturer string descriptor
ROM struct{BYTE bLength;BYTE bDscType;WORD string[25];}sd001={
sizeof(sd001),USB_DESCRIPTOR_STRING,
{'M','i','c','r','o','c','h','i','p',' ',
'T','e','c','h','n','o','l','o','g','y',' ','I','n','c','.'
}};

//Product string descriptor
ROM struct{BYTE bLength;BYTE bDscType;WORD string[22];}sd002={
sizeof(sd002),USB_DESCRIPTOR_STRING,
{'U','S','B',' ','M','i','c','r','o','p','h','o','n','e',' ','E','x','a','m','p','l','e'}};

// $TAG$FTW$
// Class specific descriptor - HID 
ROM struct{BYTE report[HID_RPT01_SIZE];}hid_rpt01={
{
    0x06, 0x00, 0xFF,       // Usage Page = 0xFF00 (Vendor Defined Page 1)
    0x09, 0x01,             // Usage (Vendor Usage 1)
    0xA1, 0x01,             // Collection (Application)
    0x19, 0x01,             //      Usage Minimum 
    0x29, 0x40,             //      Usage Maximum 	//64 input usages total (0x01 to 0x40)
    0x15, 0x00,             //      Logical Minimum (data bytes in the report may have minimum value = 0x00)
    0x26, 0xFF, 0x00, 	  	//      Logical Maximum (data bytes in the report may have maximum value = 0x00FF = unsigned 255)
    0x75, 0x08,             //      Report Size: 8-bit field size
    0x95, 0x40,             //      Report Count: Make sixty-four 8-bit fields (the next time the parser hits an "Input", "Output", or "Feature" item)
    0x81, 0x00,             //      Input (Data, Array, Abs): Instantiates input packet fields based on the above report size, count, logical min/max, and usage.
    0x19, 0x01,             //      Usage Minimum 
    0x29, 0x40,             //      Usage Maximum 	//64 output usages total (0x01 to 0x40)
    0x91, 0x00,             //      Output (Data, Array, Abs): Instantiates output packet fields.  Uses same report size and count as "Input" fields, since nothing new/different was specified to the parser since the "Input" item.
    0xC0}                   // End Collection
};          

//Array of configuration descriptors
ROM BYTE *ROM USB_CD_Ptr[]=
{
    (ROM BYTE *ROM)&configDescriptor1
};

//Array of string descriptors
ROM BYTE *ROM USB_SD_Ptr[]=
{
    (ROM BYTE *ROM)&sd000,
    (ROM BYTE *ROM)&sd001,
    (ROM BYTE *ROM)&sd002
};
        

/** EOF usb_descriptors.c ***************************************************/

#endif
