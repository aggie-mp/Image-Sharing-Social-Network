package common;

import java.io.Serializable;

public class Packet implements Serializable {

  // Packet class
  private static final long serialVersionUID = 1L;

  // Used to identify the packet (0 to 9)
  public int sequenceNumber;  
  
  // A chunk of the image+description data
  public byte[] data;         

  // Constructor
  public Packet(int sequenceNumber, byte[] data) {
    this.sequenceNumber = sequenceNumber;
    this.data = data;
  }
}