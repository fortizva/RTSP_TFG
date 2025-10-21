# 🎥 Streaming Buffering & Loss Recovery Techniques
**Study and Evaluation of Basic Buffering and Loss Recovery Techniques in Streaming Applications**  
*(Trabajo Fin de Grado – Universidad de Extremadura)* 

📘 [Read the full thesis (PDF)](docs/TFG_Francisco_Ortiz.pdf)


## 🧠 Overview  
This project explores and evaluates fundamental techniques for **buffering management** and **packet loss recovery** in **real-time streaming applications**, with the goal of improving **Quality of Service (QoS)** and **Quality of Experience (QoE)** in multimedia transmissions.

It introduces the design and implementation of an **experimental streaming client-server platform** built on **RTP** and **RTSP**, featuring:  
- An adaptive **de-jitter buffer** that stabilizes playback under fluctuating network conditions.  
- A **Forward Error Correction (FEC)** mechanism for recovering lost video packets without retransmissions.  
- Integration of an **advanced codec** supporting simultaneous audio and video streams.  
- **Enhanced graphical interfaces** that visualize transmission metrics and network behavior in real time.  

Through extensive testing in **emulated network environments** (using Mininet and Wireshark), the project demonstrates how adaptive buffering and FEC strategies significantly improve playback continuity and perceived quality, even under adverse conditions.  

---

## 🧩 Key Features  
- 🎛️ Real-time multimedia transmission using **RTP/RTSP** protocols.  
- 🧮 Implementation of **FEC (Forward Error Correction)** for packet loss recovery.  
- ⏱️ **Adaptive de-jitter buffer** for stabilizing playback.  
- 🧵 **Multithreaded Java architecture** handling audio and video independently.  
- 📊 Enhanced **UI with real-time transmission statistics**.  
- 🧪 Tested with **Mininet, MiniNAM, and Wireshark** in simulated environments.  

---

## ⚗️ Experimental Evaluation  
Tests conducted under simulated networks with **10% packet loss** and **variable jitter** demonstrated:  
- Significant improvement in data integrity using FEC redundancy groups.  
- Smooth playback through jitter compensation buffers.  
- Enhanced overall QoE for real-time streaming scenarios.  

---

## 📈 Future Work Proposed 
- Integration of **modern codecs** such as H.264 and Opus.  
- Implementation of **adaptive bitrate streaming (ABR)** mechanisms.  
- Development of **monitoring dashboards** for network visualization.  
- Scalability testing with multiple clients.  

---

## 👨‍💻 Author  
**Francisco Javier Ortiz Valverde**  
Bachelor’s Degree in Software Engineering – *University of Extremadura*  
**Supervisor:** David Miguel Cortés Polo  

---

## 🇪🇸 Resumen (Español)  
Este proyecto estudia e implementa técnicas de **gestión de buffering** y **recuperación de pérdidas** en aplicaciones de *streaming* en tiempo real, con el objetivo de mejorar la **calidad de transmisión multimedia** y la **experiencia del usuario (QoE)**.  

Se desarrolló una aplicación experimental cliente-servidor basada en **RTP y RTSP**, que incorpora un **buffer de de-jitter** adaptativo y un sistema **FEC (Forward Error Correction)** para recuperar paquetes de vídeo perdidos sin retransmisiones. Además, incluye un **códec avanzado** para audio y vídeo simultáneos y una interfaz renovada con **estadísticas en tiempo real**.  

Las pruebas realizadas en entornos de red emulados demuestran que estas técnicas mejoran considerablemente la **fluidez y estabilidad** de la reproducción, incluso bajo condiciones adversas.  

**Palabras clave:** RTP, RTSP, FEC, De-Jitter Buffer, QoS, QoE, Streaming en tiempo real.  
