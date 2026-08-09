// IAlarmReaderService.aidl
package com.dakomi.smartspacer.alarms.service;

interface IAlarmReaderService {
    String getDumpsysAlarm();
    String getLogcat(String tag);
}
