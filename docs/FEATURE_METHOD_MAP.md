# Feature Method Map

ClinicPulse is organized by active feature workflow. This map points to the classes and methods most likely to be asked about during a code review or presentation.

## Patient Management

| Question | File | Method |
| --- | --- | --- |
| Where is the patient table loaded? | `src/pages/patient/patient_directory/PatientDirectoryRepository.java` | `findPatientsForDirectory(...)` |
| Where is patient search/status filtering built? | `src/pages/patient/patient_directory/PatientDirectoryController.java` | `buildFilter()` |
| Where is the Add Patient button handled? | `src/pages/patient/patient_directory/PatientDirectoryController.java` | `addPatient()` |
| Where is Edit Patient opened from the table? | `src/pages/patient/patient_directory/PatientDirectoryController.java` | `editPatient(...)` |
| Where is Add Vitals opened from the table? | `src/pages/patient/patient_directory/PatientDirectoryController.java` | `addVitals(...)` |

## Patient Registration

| Question | File | Method |
| --- | --- | --- |
| Where is the Add/Edit Patient dialog controlled? | `src/pages/patient/patient_registration/PatientRegistrationController.java` | `showCreateDialog(...)`, `showEditDialog(...)` |
| Where is Patient ID checked for returning patients? | `src/pages/patient/patient_registration/PatientRegistrationController.java` | `checkExistingPatientId()` |
| Where is form data prepared for saving? | `src/pages/patient/patient_registration/PatientRegistrationController.java` | `buildRecord()` |
| Where is a new patient validated and saved? | `src/pages/patient/patient_registration/PatientRegistrationService.java` | `createNewPatient(...)` |
| Where is an existing patient edited? | `src/pages/patient/patient_registration/PatientRegistrationService.java` | `updateExistingPatient(...)` |
| Where does the app check whether a patient ID exists? | `src/pages/patient/patient_registration/PatientRegistrationRepository.java` | `patientIdExists(...)` |
| Where is a new patient inserted into SQLite? | `src/pages/patient/patient_registration/PatientRegistrationRepository.java` | `createPatient(...)` |
| Where is an existing patient updated in SQLite? | `src/pages/patient/patient_registration/PatientRegistrationRepository.java` | `updateExistingPatient(...)` |

## Patient File

| Question | File | Method |
| --- | --- | --- |
| Where is the patient profile loaded? | `src/pages/patient/patient_details/PatientDetailsController.java` | `loadPatient(...)` |
| Where are full patient details loaded from SQLite? | `src/pages/patient/patient_details/PatientDetailsRepository.java` | `findPatientDetailsById(...)` |
| Where is visit history loaded? | `src/pages/patient/patient_details/PatientVisitService.java` | `getVisitHistory(...)` |
| Where is a patient discharged? | `src/pages/patient/patient_registration/PatientRegistrationService.java` | `dischargePatient(...)` |

## Patient Vitals

| Question | File | Method |
| --- | --- | --- |
| Where is the Add Vitals dialog controlled? | `src/pages/patient/patient_vitals/VitalsEntryController.java` | `showDialog(...)` |
| Where is a vital reading saved? | `src/pages/patient/patient_vitals/VitalsWriteService.java` | `enterVitalReading(...)` |
| Where are abnormal vital thresholds evaluated? | `src/pages/patient/patient_vitals/VitalThresholdService.java` | `evaluate(...)` |
| Where are vital readings inserted into SQLite? | `src/pages/patient/patient_vitals/SqliteVitalReadingDao.java` | `insertVitalReading(...)` |
| Where are vitals trends loaded? | `src/pages/patient/patient_vitals/VitalsTrendService.java` | `loadTrend(...)` |

## Medical Files

| Question | File | Method |
| --- | --- | --- |
| Where is the Medical Records page controlled? | `src/pages/patient/medical_files/MedicalFilesController.java` | `loadFiles()` |
| Where is the upload dialog controlled? | `src/pages/patient/medical_files/MedicalFileUploadController.java` | `showDialog(...)` |
| Where is upload validation and file copy handled? | `src/pages/patient/medical_files/MedicalFileUploadService.java` | `uploadMedicalFile(...)` |
| Where is medical file metadata inserted into SQLite? | `src/pages/patient/medical_files/SqliteMedicalFileDao.java` | `insertUploadedFile(...)` |
