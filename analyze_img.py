import cv2
import numpy as np

def analyze_axes(img_path):
    img = cv2.imread(img_path)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # edge detection
    edges = cv2.Canny(gray, 50, 150, apertureSize=3)
    
    # find lines
    lines = cv2.HoughLinesP(edges, 1, np.pi/180, threshold=100, minLineLength=100, maxLineGap=10)
    
    h, w = img.shape[:2]
    
    min_x, max_x = w, 0
    min_y, max_y = h, 0
    
    if lines is not None:
        for line in lines:
            x1, y1, x2, y2 = line.flatten()
            # horizontal line
            if abs(y1 - y2) < 5:
                if x1 < min_x: min_x = x1
                if x2 > max_x: max_x = x2
                if y1 > max_y: max_y = y1
                if y1 < min_y: min_y = y1
            # vertical line
            if abs(x1 - x2) < 5:
                if y1 < min_y: min_y = y1
                if y2 > max_y: max_y = y2
                if x1 > max_x: max_x = x1
                if x1 < min_x: min_x = x1
                
    print(f"Detected bounding box from lines: x={min_x}-{max_x}, y={min_y}-{max_y}")
    
    # Let's also check the red pixels bounding box
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    mask1 = cv2.inRange(hsv, np.array([0, 70, 50]), np.array([10, 255, 255]))
    mask2 = cv2.inRange(hsv, np.array([170, 70, 50]), np.array([180, 255, 255]))
    mask = mask1 | mask2
    
    pts = np.where(mask > 0)
    if len(pts[0]) > 0:
        red_min_x, red_max_x = np.min(pts[1]), np.max(pts[1])
        red_min_y, red_max_y = np.min(pts[0]), np.max(pts[0])
        print(f"Red pixels bounding box: x={red_min_x}-{red_max_x}, y={red_min_y}-{red_max_y}")
        
analyze_axes('extracted_images/slide_4_img_1.png')
