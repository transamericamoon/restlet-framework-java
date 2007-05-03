/*
 * Copyright 2005-2006 J�r�me LOUVEL
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.txt
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * http://www.opensource.org/licenses/cddl1.txt
 * If applicable, add the following below this CDDL
 * HEADER, with the fields enclosed by brackets "[]"
 * replaced with your own identifying information:
 * Portions Copyright [yyyy] [name of copyright owner]
 */

package com.noelios.restlet.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Date manipulation utilities.
 */
public class DateUtils
{
   /** Preferred HTTP date format (RFC 1123). */
   public static final String FORMAT_RFC_1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";

   /** Obsoleted HTTP date format (RFC 1036). */
   public static final String FORMAT_RFC_1036 = "EEEE, dd-MMM-yy HH:mm:ss zzz";

   /** Obsoleted HTTP date format (ANSI C asctime() format). */
   public static final String FORMAT_ASC_TIME = "EEE MMM dd HH:mm:ss yyyy";
   
   /** Remember the often used GMT time zone. */
   private static final TimeZone gmtTimeZone = TimeZone.getTimeZone("GMT");

   /**
    * Compares two date with a precision set to the second.
    * @param baseDate The base date
    * @param afterDate The date supposed to be after.
    * @return True if the afterDate is indeed after the baseDate.
    */
   public static boolean after(Date baseDate, Date afterDate)
   {
      long baseTime = baseDate.getTime() / 1000;
      long afterTime = afterDate.getTime() / 1000;
      return baseTime < afterTime;
   }

   /**
    * Compares two date with a precision set to the second.
    * @param baseDate The base date
    * @param beforeDate The date supposed to be before.
    * @return True if the beforeDate is indeed before the baseDate.
    */
   public static boolean before(Date baseDate, Date beforeDate)
   {
      long baseTime = baseDate.getTime() / 1000;
      long beforeTime = beforeDate.getTime() / 1000;
      return beforeTime < baseTime;
   }

   /**
    * Compares two date with a precision set to the second.
    * @param baseDate The base date
    * @param otherDate The other date supposed to be equals.
    * @return True if both dates are equals.
    */
   public static boolean equals(Date baseDate, Date otherDate)
   {
      long baseTime = baseDate.getTime() / 1000;
      long otherTime = otherDate.getTime() / 1000;
      return otherTime == baseTime;
   }

   /**
    * Formats a Date according to the HTTP or Cookie specification.
    * @param date The date to format.
    * @param format The date format to use.
    * @return The formatted date.
    */
   public static String format(Date date, String format)
   {
      if(date == null) 
      {
         throw new IllegalArgumentException("Date is null");
      }
      else
      {
         SimpleDateFormat formatter = new SimpleDateFormat(format, Locale.US);
         formatter.setTimeZone(gmtTimeZone);
         return formatter.format(date);
      }
   }

   /**
    * Parses a HTTP date into a Date object.
    * @param httpDate The HTTP date to parse.
    * @param format The date format to use.
    * @return The parsed date.
    */
   public static Date parse(String httpDate, String format)
   {
      if(httpDate == null) 
      {
         throw new IllegalArgumentException("Date is null");
      }
      else
      {
         SimpleDateFormat parser = new SimpleDateFormat(format, Locale.US);
         parser.setTimeZone(gmtTimeZone);
         
         try
         {
            return parser.parse(httpDate);
         }
         catch(ParseException e)
         {
            return null;
         }
      }
   }
   
}
